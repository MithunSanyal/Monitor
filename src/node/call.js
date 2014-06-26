var http = require('http');
var mongo = require('mongodb');
var _ = require('underscore')._;
var express = require('express');

var file = 'config.json';

var jf = require('jsonfile'),
    util = require('util');
var databaseUrl = "statsdb"; 
var collections = ["vms", "vmstats"];
var db = require("mongojs").connect(databaseUrl, collections);

var app = express();

app.get('/getAllVms', function(req, res) {
	db.vms.find({}, function(err, items) {
		res.send(items);
		});
});
app.get('/getAllMaxVmStats',  function(req, res) {
	db.vmstats.find({"Type" : "Maximum"}, function(err, items) {
		res.send(items);
		});
});
app.get('/getAllAvgVmStats',  function(req, res) {
	db.vmstats.find({"Type" : "Average"}, function(err, items) {
		res.send(items);
		});
});
app.get('/getAllMinVmStats',  function(req, res) {
	db.vmstats.find({"Type" : "Minimum"}, function(err, items) {
		res.send(items);
		});
});


app.listen(3000);

var userConfig = jf.readFileSync(file);
var hostVal = userConfig.host;
var portVal = userConfig.port;
var caNoVal = userConfig.caNo;
var interval = userConfig.interval;

var vmsPath = "/Monitor/getAllVirtualMachines?caNo=" + caNoVal;

var vmsOps = {
	    host: hostVal,
	    port: portVal,
	    path: vmsPath,
	    method: "GET"
};

//var avgVmStatsPath = "/Monitor/getAllAvgVMStatistics?caNo=" + caNoVal + "&from=2014-06-04T04:02:00&to=2014-06-04T04:00:00"; 
var avgVmStatsPath = "/Monitor/getAllAvgVMStatistics?caNo=" + caNoVal;
var avgVmStatsOps = {
		 host: hostVal,
		 port: portVal,
		 path: avgVmStatsPath,
		 method: "GET"
};

var maxVmStatsPath = "/Monitor/getAllMaxVMStatistics?caNo=" + caNoVal;  
var maxVmStatsOps = {
		 host: hostVal,
		 port: portVal,
		 path: maxVmStatsPath,
		 method: "GET"	
};

var minVmStatsPath = "/Monitor/getAllMinVMStatistics?caNo=" + caNoVal;  
var minVmStatsOps = {
		 host: hostVal,
		 port: portVal,
		 path: minVmStatsPath,
		 method: "GET"	
};

/**
 * Initial Call
 */
getStats(vmsOps, "vms");
getStats(avgVmStatsOps, "vmstats");
getStats(maxVmStatsOps, "vmstats");
getStats(minVmStatsOps, "vmstats");

/**
 * Interval Call
 */
setInterval(function() {getStats(vmsOps, "vms");}, interval);
setInterval(function() {getStats(avgVmStatsOps, "vmstats");}, interval);
setInterval(function() {getStats(maxVmStatsOps, "vmstats");}, interval);
setInterval(function() {getStats(minVmStatsOps, "vmstats");}, interval);


function getStats(options, table) {
	// do the GET request
	var reqGet = http.request(options, function(res) {    
	    var responseString = '';
	    console.log('creating table');
	    res.on('data', function(data) {
	    	responseString += data;
	    });

	    res.on('end', function() {
	    	
	    	responseString  = '{\"results\": ' + responseString + '}';
	    	var jsonData = JSON.parse(responseString);
	    	if (table == 'vms') {
	    		db.vms.remove({}); // Remove all stale records
	    		// Set a gap of 10 secs for db clearing operation
	    		setTimeout(function() {
	    		}, 10000);
	    		_.each(jsonData.results, function(result) {
	    			db.vms.save(result, function(err, saved) {
	    			if( err || !saved ) console.log("Inside vms - User not saved");
	    			else console.log("Inside vms - User saved");
	    			});
	    		});
	    	} else if (table == 'vmstats') {
	    		db.vmstats.remove({}); // Remove all stale records
	    		// Set a gap of 10 secs for db clearing operation
	    		setTimeout(function() {
	    		}, 10000);
	    		_.each(jsonData.results, function(result) {
	    			db.vmstats.save(result, function(err, saved) {
	    			if( err || !saved ) console.log("Inside vmstats - User not saved");
	    			else console.log("Inside vmstats - User saved");
	    			});
	    		});
	    	}
	    	
	    });

	});
	 
	reqGet.on('error', function(e) {
	    console.error(e);
	});

	reqGet.end();

}