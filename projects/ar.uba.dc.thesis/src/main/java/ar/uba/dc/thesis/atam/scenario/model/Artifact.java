package ar.uba.dc.thesis.atam.scenario.model;

import ar.uba.dc.thesis.common.ThesisPojo;

import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

public class Artifact extends ThesisPojo {

	@XStreamAsAttribute
	private final String name;

	@XStreamAsAttribute
	private final String systemName;

	public Artifact(String systemName, String name) {
		super();
		this.systemName = systemName;
		this.name = name;

		this.validate();
	}

	public String getName() {
		return this.name;
	}

	public String getSystemName() {
		return systemName;
	}

	public void validate() {
		// Do nothing
	}
}
