package com.trov.monitor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/")
public class CloudMonitor extends Application {

	/**
	 * Gets the implementation class for this application
	 */
	public Set<Class<?>> getClasses() {
		return new HashSet<Class<?>>(Arrays.asList(
				com.trov.monitor.CloudMonitorImpl.class));
	}
}

