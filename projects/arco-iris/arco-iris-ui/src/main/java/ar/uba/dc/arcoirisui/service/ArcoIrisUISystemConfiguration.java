package ar.uba.dc.arcoirisui.service;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import commons.core.BaseSystemConfiguration;

public class ArcoIrisUISystemConfiguration extends BaseSystemConfiguration {

	private static final long serialVersionUID = 1L;

	/*
	 * Do not change the name of the following properties because they are used in the build process
	 */
	private static final String BACKEND_DESCRIPTORS = "BACKEND_DESCRIPTORS";

	private static ArcoIrisUISystemConfiguration instance = new ArcoIrisUISystemConfiguration();

	public static ArcoIrisUISystemConfiguration getInstance() {
		return instance;
	}

	private ArcoIrisUISystemConfiguration() {
		super();
	}

	public List<String> getServicesDescriptorNames() {
		List<String> result = new ArrayList<String>();

		String backendDescriptors = this.getProperty(BACKEND_DESCRIPTORS);

		if (backendDescriptors != null) {
			StringTokenizer st = new StringTokenizer(backendDescriptors, ",");
			while (st.hasMoreTokens()) {
				result.add(st.nextToken());
			}
		}

		return result;
	}
}