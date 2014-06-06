package com.trov.monitor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONArray;
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.compute.ComputeServices;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.compute.VmStatistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Path("/")
public class CloudMonitorImpl {

	final static Logger logger = LoggerFactory.getLogger("CloudMonitorImpl");
	
	private static Map<String, CloudAccount> cloudAccounts = new HashMap<String, CloudAccount>();
	String[] AWSRegions = {"us-east-1","sa-east-1","eu-west-1","ap-northeast-1","us-west-2","us-west-1","ap-southeast-1"};
	String[] GoogleRegions = {"us-central1"};
	
	String[] endpoints = {"https://api.opsourcecloud.net", "https://ec2.us-east-1.amazonaws.com/","https://ec2.us-west-1.amazonaws.com/","https://ec2.us-west-2.amazonaws.com/","https://ec2.sa-east-1.amazonaws.com/",
			"https://ec2.eu-west-1.amazonaws.com/","https://ec2.ap-southeast-1.amazonaws.com/","https://ec2.ap-southeast-2.amazonaws.com/","https://ec2.ap-northeast-1.amazonaws.com/",
			"https://ec2.us-east-1.amazonaws.com","https://ec2.us-west-1.amazonaws.com","https://ec2.us-west-2.amazonaws.com","https://ec2.sa-east-1.amazonaws.com",
			"https://ec2.eu-west-1.amazonaws.com", "https://ec2.ap-southeast-2.amazonaws.com","https://ec2.ap-northeast-1.amazonaws.com"};
	
	public CloudMonitorImpl() {
		
	}
	
	
	/**
	 * Register the cloud account with Monitoring Service
	 * JSON request format for AWS:
	 * {
    	"cloud": {
        	"cloudName": <cloudname>,
        	"cloudAccountNumber": <cloudAccountNumber>,
        	"cloudAccountName": <cloudAccountName>,
        	"cloudAccess": {
            	"apiSharedKey": <apiSharedkey>,
            	"apiSecretKey": <apiSecretKey>,
            	"endPoint": <endpoint>
        	}
    		}
		}

	 */
	@POST
	@Path("/registerCloudAccount")
	@Produces(MediaType.APPLICATION_JSON)
	public Response registerCloudAccount(JSONObject request) {
		
		try
		{
			CloudAccount ca = new CloudAccount();
			
			JSONObject cloudObj = request.getJSONObject("cloud");
			String caNo = cloudObj.getString("cloudAccountNumber");
			JSONObject cloudAccessObj = cloudObj.getJSONObject("cloudAccess");    
			ca.setApiSharedKey(cloudAccessObj.getString("apiSharedKey"));
			ca.setApiSecretKey(cloudAccessObj.getString("apiSecretKey"));
			ca.setEndpoint(cloudAccessObj.getString("endPoint"));
			ca.setCloudAccountName(cloudObj.getString("cloudAccountName"));
			ca.setCloudAccountNumber(caNo);
			ca.setCloudName(cloudObj.getString("cloudName"));
					
			cloudAccounts.put(caNo, ca);

		}catch (Exception e) {
			logger.error("registerCloudAccount: " + e.getLocalizedMessage());
			JSONObject error = new JSONObject();
			try {
				error.put("error", e.getLocalizedMessage());
				return Response.status(600).type(MediaType.APPLICATION_JSON)
						.entity(error).build();
			} catch (JSONException e1) {
				return Response.status(500).type(MediaType.APPLICATION_JSON)
						.entity("uknown error").build();
			}	
		}
		
		return Response.status(200).type(MediaType.APPLICATION_JSON)
				.entity("success").build(); 
	}
	
	/**
	 * Get average metrics for all VMs in the given cloud account
	 * @param cloudAccountNumber
	 * @param from
	 * @param to
	 * @return
	 */
	@GET
	@Path("/getAllAvgVMStatistics")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllAvgVMStatistics(@QueryParam("caNo") String cloudAccountNumber, @QueryParam("from") String from, @QueryParam("to") String to) {
		CloudAccount ca = cloudAccounts.get(cloudAccountNumber);
		String[] regions = null;
		JSONArray response = new JSONArray();
		long fromEpoch = getEpochTime(from);
		long toEpoch = getEpochTime(to);
		try{
    	
			if (ca.getCloudName().equals("aws"))
				regions = AWSRegions;
			else if (ca.getCloudName().equals("google"))
				regions = GoogleRegions;

			if (regions != null)
				for(int i=0;i < regions.length; i++)
				{
					TrovCloudProvider trovCloudProvider = new TrovCloudProvider();
					CloudProvider provider = trovCloudProvider.getCloudProvider(ca, regions[i]);
					ComputeServices compute = provider.getComputeServices();

					/*Google googleCloud = new Google();
					googleCloud.connect(provider.getContext(), provider.getComputeCloud());

					GoogleCompute compute = googleCloud.getComputeServices();*/
					if( compute == null ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no compute support").build();
					}
					// of the supported compute services, are VMs supported?
					if( !compute.hasVirtualMachineSupport() ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no vm support").build();
					}

					// list the virtual machines
					VirtualMachineSupport vms = compute.getVirtualMachineSupport();	

					Iterable<VirtualMachine> vmIterable = vms.listVirtualMachines();
					Iterator<VirtualMachine> vmItr = vmIterable.iterator();

					while(vmItr.hasNext()) {
						VirtualMachine vm = vmItr.next();

						JSONObject vmObject = new JSONObject();
						vmObject.put("vmName", vm.getName());
						vmObject.put("vmId", vm.getProviderVirtualMachineId());
						vmObject.put("vmState", vm.getCurrentState());
						if (vm.getCurrentState().toString().equalsIgnoreCase("running")) {
							VmStatistics stats = vms.getVMStatistics(vm.getProviderVirtualMachineId(), fromEpoch, toEpoch);

							vmObject.put("Average CPU Utilization (Percent)", stats.getAverageCpuUtilization());
							vmObject.put("Average Disk Reads (Operations)", stats.getAverageDiskReadOperations());
							vmObject.put("Average Disk Writes (Operations)", stats.getAverageDiskWriteOperations());
							vmObject.put("Average Disk Reads (Bytes)", stats.getAverageDiskReadBytes());
							vmObject.put("Average Disk Writes (Bytes)", stats.getAverageDiskWriteBytes());
							vmObject.put("Average Network Input (Bytes)", stats.getAverageNetworkIn());
							vmObject.put("Average Network Output (Bytes)", stats.getAverageNetworkOut());

						}
						response.put(vmObject);
					}	
				}
		} catch (Exception e) {
			logger.error("getAllAvgVMStatistics: " + e.getLocalizedMessage());
			JSONObject error = new JSONObject();
			try {
				error.put("error", e.getLocalizedMessage());
				return Response.status(600).type(MediaType.APPLICATION_JSON)
						.entity(error).build();
			} catch (JSONException e1) {
				return Response.status(500).type(MediaType.APPLICATION_JSON)
						.entity("uknown error").build();
			}		
		}

		return Response.status(200).type(MediaType.APPLICATION_JSON)
				.entity(response).build(); 
		}
	
	/**
	 * Get maximum values of all metrics of all VMs in a given cloud account
	 * @param cloudAccountNumber
	 * @param from
	 * @param to
	 * @return
	 */
	@GET
	@Path("/getAllMaxVMStatistics")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllMaxVMStatistics(@QueryParam("caNo") String cloudAccountNumber, 
			@QueryParam("from") String from,
			@QueryParam("to") String to) {
		CloudAccount ca = cloudAccounts.get(cloudAccountNumber);
		String[] regions = null;
		JSONArray response = new JSONArray();
		long fromEpoch = getEpochTime(from);
		long toEpoch = getEpochTime(to);
		try{
    	
			if (ca.getCloudName().equals("aws"))
				regions = AWSRegions;
			else if (ca.getCloudName().equals("google"))
				regions = GoogleRegions;

			if (regions != null)
				for(int i=0;i < regions.length; i++)
				{
					TrovCloudProvider trovCloudProvider = new TrovCloudProvider();
					CloudProvider provider = trovCloudProvider.getCloudProvider(ca, regions[i]);
					ComputeServices compute = provider.getComputeServices();

					/*Google googleCloud = new Google();
					googleCloud.connect(provider.getContext(), provider.getComputeCloud());

					GoogleCompute compute = googleCloud.getComputeServices();*/
					if( compute == null ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no compute support").build();
					}
					// of the supported compute services, are VMs supported?
					if( !compute.hasVirtualMachineSupport() ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no vm support").build();
					}

					// list the virtual machines
					VirtualMachineSupport vms = compute.getVirtualMachineSupport();	

					Iterable<VirtualMachine> vmIterable = vms.listVirtualMachines();
					Iterator<VirtualMachine> vmItr = vmIterable.iterator();

					while(vmItr.hasNext()) {
						VirtualMachine vm = vmItr.next();

						JSONObject vmObject = new JSONObject();
						vmObject.put("vmName", vm.getName());
						vmObject.put("vmId", vm.getProviderVirtualMachineId());
						vmObject.put("vmState", vm.getCurrentState());
						if (vm.getCurrentState().toString().equalsIgnoreCase("running")) {
							VmStatistics stats = vms.getVMStatistics(vm.getProviderVirtualMachineId(), fromEpoch, toEpoch);

							vmObject.put("Maximum CPU Utilization (Percent)", stats.getMaximumCpuUtilization());
							vmObject.put("Maximum Disk Reads (Operations)", stats.getMaximumDiskReadOperations());
							vmObject.put("Maximum Disk Writes (Operations)", stats.getMaximumDiskWriteOperations());
							vmObject.put("Maximum Disk Reads (Bytes)", stats.getMaximumDiskReadBytes());
							vmObject.put("Maximum Disk Writes (Bytes)", stats.getMaximumDiskWriteBytes());
							vmObject.put("Maximum Network Input (Bytes)", stats.getMaximumNetworkIn());
							vmObject.put("Maximum Network Output (Bytes)", stats.getMaximumNetworkOut());

						}
						response.put(vmObject);
					}	
				}
		} catch (Exception e) {
			logger.error("getAllMaxVMStatistics: " + e.getLocalizedMessage());
			JSONObject error = new JSONObject();
			try {
				error.put("error", e.getLocalizedMessage());
				return Response.status(600).type(MediaType.APPLICATION_JSON)
						.entity(error).build();
			} catch (JSONException e1) {
				return Response.status(500).type(MediaType.APPLICATION_JSON)
						.entity("uknown error").build();
			}		
		}

		return Response.status(200).type(MediaType.APPLICATION_JSON)
				.entity(response).build(); 
	}
	
	/**
	 * Get the minimum value of all metrics of all VMs in a given cloud account
	 * @param cloudAccountNumber
	 * @param from
	 * @param to
	 * @return
	 */
	@GET
	@Path("/getAllMinVMStatistics")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllMinVMStatistics(@QueryParam("caNo") String cloudAccountNumber, 
			@QueryParam("from") String from,
			@QueryParam("to") String to) {
		
		CloudAccount ca = cloudAccounts.get(cloudAccountNumber);
		String[] regions = null;
		JSONArray response = new JSONArray();
		long fromEpoch = getEpochTime(from);
		long toEpoch = getEpochTime(to);
		try{
    	
			if (ca.getCloudName().equals("aws"))
				regions = AWSRegions;
			else if (ca.getCloudName().equals("google"))
				regions = GoogleRegions;

			if (regions != null)
				for(int i=0;i < regions.length; i++)
				{
					TrovCloudProvider trovCloudProvider = new TrovCloudProvider();
					CloudProvider provider = trovCloudProvider.getCloudProvider(ca, regions[i]);
					ComputeServices compute = provider.getComputeServices();

					/*Google googleCloud = new Google();
					googleCloud.connect(provider.getContext(), provider.getComputeCloud());

					GoogleCompute compute = googleCloud.getComputeServices();*/
					if( compute == null ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no compute support").build();
					}
					// of the supported compute services, are VMs supported?
					if( !compute.hasVirtualMachineSupport() ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no vm support").build();
					}

					// list the virtual machines
					VirtualMachineSupport vms = compute.getVirtualMachineSupport();	

					Iterable<VirtualMachine> vmIterable = vms.listVirtualMachines();
					Iterator<VirtualMachine> vmItr = vmIterable.iterator();

					while(vmItr.hasNext()) {
						VirtualMachine vm = vmItr.next();

						JSONObject vmObject = new JSONObject();
						vmObject.put("vmName", vm.getName());
						vmObject.put("vmId", vm.getProviderVirtualMachineId());
						vmObject.put("vmState", vm.getCurrentState());
						if (vm.getCurrentState().toString().equalsIgnoreCase("running")) {
							VmStatistics stats = vms.getVMStatistics(vm.getProviderVirtualMachineId(), fromEpoch, toEpoch);

							vmObject.put("Minimum CPU Utilization (Percent)", stats.getMinimumCpuUtilization());
							vmObject.put("Minimum Disk Reads (Operations)", stats.getMinimumDiskReadOperations());
							vmObject.put("Minimum Disk Writes (Operations)", stats.getMinimumDiskWriteOperations());
							vmObject.put("Minimum Disk Reads (Bytes)", stats.getMinimumDiskReadBytes());
							vmObject.put("Minimum Disk Writes (Bytes)", stats.getMinimumDiskWriteBytes());
							vmObject.put("Minimum Network Input (Bytes)", stats.getMinimumNetworkIn());
							vmObject.put("Minimum Network Output (Bytes)", stats.getMinimumNetworkOut());

						}
						response.put(vmObject);
					}	
				}
		} catch (Exception e) {
			logger.error("getAllMinVMStatistics: " + e.getLocalizedMessage());
			JSONObject error = new JSONObject();
			try {
				error.put("error", e.getLocalizedMessage());
				return Response.status(600).type(MediaType.APPLICATION_JSON)
						.entity(error).build();
			} catch (JSONException e1) {
				return Response.status(500).type(MediaType.APPLICATION_JSON)
						.entity("uknown error").build();
			}		
		}

		return Response.status(200).type(MediaType.APPLICATION_JSON)
				.entity(response).build(); 
	}
	
	/**
	 * Get information about all VMs in a given cloud account
	 * @param cloudAccountNumber
	 * @return
	 */
	@GET
	@Path("/getAllVirtualMachines")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllVirtualMachines(@QueryParam("caNo") String cloudAccountNumber) {
		CloudAccount ca = cloudAccounts.get(cloudAccountNumber);
		String[] regions = null;
		JSONArray response = new JSONArray();
		try{
		
		if (ca.getCloudName().equals("aws"))
			regions = AWSRegions;
		//else if (ca.getCloudName().equals("opsource"))
			//regions = OpsourceRegions;
		
			if (regions != null)
				for(int i=0;i < regions.length; i++)
				{
					TrovCloudProvider discoveryAgent = new TrovCloudProvider();
					CloudProvider provider = discoveryAgent.getCloudProvider(ca, regions[i]);
					ComputeServices compute = provider.getComputeServices();
					
					
					if( compute == null ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no compute support").build();
					}
					// of the supported compute services, are VMs supported?
					if( !compute.hasVirtualMachineSupport() ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no vm support").build();
					}
					// list the virtual machines
					VirtualMachineSupport vms = compute.getVirtualMachineSupport();	
					//EC2Instance vms = compute.getVirtualMachineSupport();
					
					Iterable<VirtualMachine> vmIterable = vms.listVirtualMachines();
					Iterator<VirtualMachine> vmItr = vmIterable.iterator();
					
					while(vmItr.hasNext()) {
						VirtualMachine vm = vmItr.next();
					
						JSONObject vmObject = new JSONObject();
						vmObject.put("vmName", vm.getName());
						vmObject.put("vmId", vm.getProviderVirtualMachineId());
						vmObject.put("vmArchitecture", vm.getArchitecture().name());
						vmObject.put("vmProductId", vm.getProductId());
						vmObject.put("vmState", vm.getCurrentState());
						vmObject.put("vmCreationTime", getTimeFromEpoch(vm.getCreationTimestamp()));
						vmObject.put("vmLastBootTime", getTimeFromEpoch(vm.getLastBootTimestamp()));
						response.put(vmObject);
					}	
				}
		} catch (Exception e) {
			logger.error("getVirtualMachines: " + e.getLocalizedMessage());
			JSONObject error = new JSONObject();
			try {
				error.put("error", e.getLocalizedMessage());
				return Response.status(600).type(MediaType.APPLICATION_JSON)
						.entity(error).build();
			} catch (JSONException e1) {
				return Response.status(500).type(MediaType.APPLICATION_JSON)
						.entity("uknown error").build();
			}		
		}

		return Response.status(200).type(MediaType.APPLICATION_JSON)
				.entity(response).build(); 
		}
	
	/**
	 * Get information about a VM given its Id
	 * @param cloudAccountNumber
	 * @param vmId
	 * @return
	 */
	@GET
	@Path("/getVirtualMachine")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getVirtualMachine(@QueryParam("caNo") String cloudAccountNumber, @QueryParam("vmId") String vmId) {
		CloudAccount ca = cloudAccounts.get(cloudAccountNumber);
		String[] regions = null;
		JSONObject vmObject = new JSONObject();
		try{

			if (ca.getCloudName().equals("aws"))
				regions = AWSRegions;

			if (regions != null)
				for(int i=0;i < regions.length; i++)
				{
					TrovCloudProvider discoveryAgent = new TrovCloudProvider();
					CloudProvider provider = discoveryAgent.getCloudProvider(ca, regions[i]);
					ComputeServices compute = provider.getComputeServices();


					if( compute == null ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no compute support").build();
					}
					// of the supported compute services, are VMs supported?
					if( !compute.hasVirtualMachineSupport() ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no vm support").build();
					}
					// list the virtual machines
					VirtualMachineSupport vms = compute.getVirtualMachineSupport();	


					VirtualMachine vm = vms.getVirtualMachine(vmId);

					vmObject.put("vmName", vm.getName());
					vmObject.put("vmId", vm.getProviderVirtualMachineId());
					vmObject.put("vmArchitecture", vm.getArchitecture().name());
					vmObject.put("vmProductId", vm.getProductId());
					vmObject.put("vmState", vm.getCurrentState());
					vmObject.put("vmCreationTime", getTimeFromEpoch(vm.getCreationTimestamp()));
					vmObject.put("vmLastBootTime", getTimeFromEpoch(vm.getLastBootTimestamp()));

				}
		} catch (Exception e) {
			logger.error("getVirtualMachines: " + e.getLocalizedMessage());
			JSONObject error = new JSONObject();
			try {
				error.put("error", e.getLocalizedMessage());
				return Response.status(600).type(MediaType.APPLICATION_JSON)
						.entity(error).build();
			} catch (JSONException e1) {
				return Response.status(500).type(MediaType.APPLICATION_JSON)
						.entity("uknown error").build();
			}		
		}

		return Response.status(200).type(MediaType.APPLICATION_JSON)
				.entity(vmObject).build(); 
	}
	
	/**
	 * Get average value of all metrics of a VM given its Id
	 * @param cloudAccountNumber
	 * @param from
	 * @param to
	 * @param vmId
	 * @return
	 */
	@GET
	@Path("/getAvgVMStatistics")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAvgVMStatistics(@QueryParam("caNo") String cloudAccountNumber,
			@QueryParam("from") String from,
			@QueryParam("to") String to,
			@QueryParam("vmId") String vmId) {
		
		CloudAccount ca = cloudAccounts.get(cloudAccountNumber);
		String[] regions = null;
		long fromEpoch = getEpochTime(from);
		long toEpoch = getEpochTime(to);
		JSONObject vmObject = new JSONObject();
		try{

			if (ca.getCloudName().equals("aws"))
				regions = AWSRegions;

			if (regions != null)
				for(int i=0;i < regions.length; i++)
				{
					TrovCloudProvider discoveryAgent = new TrovCloudProvider();
					CloudProvider provider = discoveryAgent.getCloudProvider(ca, regions[i]);
					ComputeServices compute = provider.getComputeServices();


					if( compute == null ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no compute support").build();
					}
					// of the supported compute services, are VMs supported?
					if( !compute.hasVirtualMachineSupport() ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no vm support").build();
					}
					// list the virtual machines
					VirtualMachineSupport vms = compute.getVirtualMachineSupport();	


					VirtualMachine vm = vms.getVirtualMachine(vmId);

					vmObject.put("vmName", vm.getName());
					vmObject.put("vmId", vm.getProviderVirtualMachineId());
					vmObject.put("vmState", vm.getCurrentState());
					if (vm.getCurrentState().toString().equalsIgnoreCase("running")) {
						VmStatistics stats = vms.getVMStatistics(vm.getProviderVirtualMachineId(), fromEpoch, toEpoch);

						vmObject.put("Average CPU Utilization (Percent)", stats.getAverageCpuUtilization());
						vmObject.put("Average Disk Reads (Operations)", stats.getAverageDiskReadOperations());
						vmObject.put("Average Disk Writes (Operations)", stats.getAverageDiskWriteOperations());
						vmObject.put("Average Disk Reads (Bytes)", stats.getAverageDiskReadBytes());
						vmObject.put("Average Disk Writes (Bytes)", stats.getAverageDiskWriteBytes());
						vmObject.put("Average Network Input (Bytes)", stats.getAverageNetworkIn());
						vmObject.put("Average Network Output (Bytes)", stats.getAverageNetworkOut());
					}
					
				}
		} catch (Exception e) {
			logger.error("getVirtualMachines: " + e.getLocalizedMessage());
			JSONObject error = new JSONObject();
			try {
				error.put("error", e.getLocalizedMessage());
				return Response.status(600).type(MediaType.APPLICATION_JSON)
						.entity(error).build();
			} catch (JSONException e1) {
				return Response.status(500).type(MediaType.APPLICATION_JSON)
						.entity("uknown error").build();
			}		
		}

		return Response.status(200).type(MediaType.APPLICATION_JSON)
				.entity(vmObject).build(); 
	}
	
	/**
	 * Get maximum value of all metrics of a VM given its Id
	 * @param cloudAccountNumber
	 * @param from
	 * @param to
	 * @param vmId
	 * @return
	 */
	@GET
	@Path("/getMaxVMStatistics")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMaxVMStatistics(@QueryParam("caNo") String cloudAccountNumber,
			@QueryParam("from") String from,
			@QueryParam("to") String to,
			@QueryParam("vmId") String vmId) {
		
		CloudAccount ca = cloudAccounts.get(cloudAccountNumber);
		String[] regions = null;
		long fromEpoch = getEpochTime(from);
		long toEpoch = getEpochTime(to);
		JSONObject vmObject = new JSONObject();
		try{

			if (ca.getCloudName().equals("aws"))
				regions = AWSRegions;

			if (regions != null)
				for(int i=0;i < regions.length; i++)
				{
					TrovCloudProvider discoveryAgent = new TrovCloudProvider();
					CloudProvider provider = discoveryAgent.getCloudProvider(ca, regions[i]);
					ComputeServices compute = provider.getComputeServices();


					if( compute == null ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no compute support").build();
					}
					// of the supported compute services, are VMs supported?
					if( !compute.hasVirtualMachineSupport() ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no vm support").build();
					}
					// list the virtual machines
					VirtualMachineSupport vms = compute.getVirtualMachineSupport();	


					VirtualMachine vm = vms.getVirtualMachine(vmId);

					vmObject.put("vmName", vm.getName());
					vmObject.put("vmId", vm.getProviderVirtualMachineId());
					vmObject.put("vmState", vm.getCurrentState());
					if (vm.getCurrentState().toString().equalsIgnoreCase("running")) {
						VmStatistics stats = vms.getVMStatistics(vm.getProviderVirtualMachineId(), fromEpoch, toEpoch);

						vmObject.put("Maximum CPU Utilization (Percent)", stats.getMaximumCpuUtilization());
						vmObject.put("Maximum Disk Reads (Operations)", stats.getMaximumDiskReadOperations());
						vmObject.put("Maximum Disk Writes (Operations)", stats.getMaximumDiskWriteOperations());
						vmObject.put("Maximum Disk Reads (Bytes)", stats.getMaximumDiskReadBytes());
						vmObject.put("Maximum Disk Writes (Bytes)", stats.getMaximumDiskWriteBytes());
						vmObject.put("Maximum Network Input (Bytes)", stats.getMaximumNetworkIn());
						vmObject.put("Maximum Network Output (Bytes)", stats.getMaximumNetworkOut());
					}
					
				}
		} catch (Exception e) {
			logger.error("getVirtualMachines: " + e.getLocalizedMessage());
			JSONObject error = new JSONObject();
			try {
				error.put("error", e.getLocalizedMessage());
				return Response.status(600).type(MediaType.APPLICATION_JSON)
						.entity(error).build();
			} catch (JSONException e1) {
				return Response.status(500).type(MediaType.APPLICATION_JSON)
						.entity("uknown error").build();
			}		
		}

		return Response.status(200).type(MediaType.APPLICATION_JSON)
				.entity(vmObject).build(); 
	}
	
	/**
	 * Get minimum value of all metrics of a VM given its Id
	 * @param cloudAccountNumber
	 * @param from
	 * @param to
	 * @param vmId
	 * @return
	 */
	@GET
	@Path("/getMinVMStatistics")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMinVMStatistics(@QueryParam("caNo") String cloudAccountNumber,
			@QueryParam("from") String from,
			@QueryParam("to") String to,
			@QueryParam("vmId") String vmId) {
		
		CloudAccount ca = cloudAccounts.get(cloudAccountNumber);
		String[] regions = null;
		long fromEpoch = getEpochTime(from);
		long toEpoch = getEpochTime(to);
		JSONObject vmObject = new JSONObject();
		try{

			if (ca.getCloudName().equals("aws"))
				regions = AWSRegions;

			if (regions != null)
				for(int i=0;i < regions.length; i++)
				{
					TrovCloudProvider discoveryAgent = new TrovCloudProvider();
					CloudProvider provider = discoveryAgent.getCloudProvider(ca, regions[i]);
					ComputeServices compute = provider.getComputeServices();


					if( compute == null ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no compute support").build();
					}
					// of the supported compute services, are VMs supported?
					if( !compute.hasVirtualMachineSupport() ) {
						return Response.status(600).type(MediaType.APPLICATION_JSON)
								.entity(provider.getProviderName() + ": no vm support").build();
					}
					// list the virtual machines
					VirtualMachineSupport vms = compute.getVirtualMachineSupport();	


					VirtualMachine vm = vms.getVirtualMachine(vmId);

					vmObject.put("vmName", vm.getName());
					vmObject.put("vmId", vm.getProviderVirtualMachineId());
					vmObject.put("vmState", vm.getCurrentState());
					if (vm.getCurrentState().toString().equalsIgnoreCase("running")) {
						VmStatistics stats = vms.getVMStatistics(vm.getProviderVirtualMachineId(), fromEpoch, toEpoch);

						vmObject.put("Minimum CPU Utilization (Percent)", stats.getMinimumCpuUtilization());
						vmObject.put("Minimum Disk Reads (Operations)", stats.getMinimumDiskReadOperations());
						vmObject.put("Minimum Disk Writes (Operations)", stats.getMinimumDiskWriteOperations());
						vmObject.put("Minimum Disk Reads (Bytes)", stats.getMinimumDiskReadBytes());
						vmObject.put("Minimum Disk Writes (Bytes)", stats.getMinimumDiskWriteBytes());
						vmObject.put("Minimum Network Input (Bytes)", stats.getMinimumNetworkIn());
						vmObject.put("Minimum Network Output (Bytes)", stats.getMinimumNetworkOut());
					}
					
				}
		} catch (Exception e) {
			logger.error("getVirtualMachines: " + e.getLocalizedMessage());
			JSONObject error = new JSONObject();
			try {
				error.put("error", e.getLocalizedMessage());
				return Response.status(600).type(MediaType.APPLICATION_JSON)
						.entity(error).build();
			} catch (JSONException e1) {
				return Response.status(500).type(MediaType.APPLICATION_JSON)
						.entity("uknown error").build();
			}		
		}

		return Response.status(200).type(MediaType.APPLICATION_JSON)
				.entity(vmObject).build(); 
	}
	
	private long getEpochTime(String time) {
		long epochTime = 0;
		try {
			TimeZone tz = TimeZone.getTimeZone("UTC");
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			df.setTimeZone(tz);
			Date date = df.parse(time);
			epochTime = date.getTime();
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
			return 0;
		}
		return epochTime;
	}
	
	private String getTimeFromEpoch(long epoch) {
		String formatted = "";
		try {
			Date date = new Date(epoch);
			DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
			formatted = format.format(date);
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		}
		return formatted;
	}
	
}

