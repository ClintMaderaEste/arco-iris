/**
 * Created April 7, 2006.
 */
package org.sa.rainbow.model.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.acmestudio.acme.ModelHelper;
import org.acmestudio.acme.core.IAcmeObject;
import org.acmestudio.acme.element.IAcmeElementInstance;
import org.acmestudio.acme.element.IAcmeElementType;
import org.acmestudio.acme.element.IAcmeSystem;
import org.acmestudio.acme.element.property.IAcmeProperty;
import org.acmestudio.acme.model.IAcmeModel;
import org.sa.rainbow.adaptation.AdaptationManager;
import org.sa.rainbow.core.AbstractRainbowRunnable;
import org.sa.rainbow.core.IModelManager;
import org.sa.rainbow.core.IRainbowRunnable;
import org.sa.rainbow.core.Oracle;
import org.sa.rainbow.core.Rainbow;
import org.sa.rainbow.event.EventTypeFilter;
import org.sa.rainbow.event.IEventService;
import org.sa.rainbow.event.IProtocolAction;
import org.sa.rainbow.event.IRainbowEventListener;
import org.sa.rainbow.event.IRainbowMessage;
import org.sa.rainbow.event.RainbowEventAdapter;
import org.sa.rainbow.health.Beacon;
import org.sa.rainbow.model.Model;
import org.sa.rainbow.monitor.SystemDelegate;
import org.sa.rainbow.monitor.TargetSystem;
import org.sa.rainbow.monitor.sim.SimulationRunner;
import org.sa.rainbow.util.Util;

/**
 * Implementing class of the RainbowModel Manager interface.
 * 
 * @author Shang-Wen Cheng (zensoul@cs.cmu.edu)
 */
public class ModelManager extends AbstractRainbowRunnable implements IModelManager {

	private SimulationRunner m_sim = null;
	private SystemDelegate m_sys = null;
	private Model m_model = null;
	private GaugeCoordinator m_gaugeCoord = null;
	private IRainbowEventListener m_healthListener = null;

	/** Beacon used as interval for computing accrued system utility */
	private Beacon m_beacon = null;
	/** Array of system utilities, with the 0th element being the accurred */
	private List<Double> m_accruedList = null;

	/**
	 * Default Constructor.
	 */
	public ModelManager () {
		super(NAME);

		m_model = Oracle.instance().rainbowModel();
		String per = Rainbow.property(Rainbow.PROPKEY_MODEL_EVAL_PERIOD);
		if (per != null) {
			m_beacon = new Beacon(Long.parseLong(per));
			m_beacon.mark();
			m_accruedList = new ArrayList<Double> ();
			m_accruedList.add(0.0);  // use zero-th entry as sum
		}
		if (Rainbow.inSimulation()) {
			// now get the simulation system
			m_sim = (SimulationRunner )Oracle.instance().targetSystem();
		} else {
			// now get the delegate of target system
			m_sys = (SystemDelegate )Oracle.instance().targetSystem();
			m_gaugeCoord = new GaugeCoordinator();
			m_healthListener = healthListener(Util.genID(getClass()));
			Rainbow.eventService().listen(IEventService.TOPIC_RAINBOW_HEALTH, m_healthListener);
		}
	}

	/* (non-Javadoc)
	 * @see org.sa.rainbow.core.IDisposable#dispose()
	 */
	public void dispose () {
		if (! Rainbow.inSimulation()) {
			m_gaugeCoord.dispose();
			Rainbow.eventService().unlisten(m_healthListener);
			// dispose of system after waiting a little bit
			Util.pause(IRainbowRunnable.LONG_SLEEP_TIME);
			if (m_sys != null) {
				m_sys.dispose();
			}
		}
		Oracle.instance().rainbowModel().dispose();

		// null-out data members
		m_gaugeCoord = null;
		m_model = null;
		m_sys = null;
		m_sim = null;
	}


	/* (non-Javadoc)
	 * @see org.sa.rainbow.core.AbstractRainbowRunnable#doTerminate()
	 */
	@Override
	protected void doTerminate() {
		// dump the accrued utility list to the data log
		double sum = 0.0;
		for (double v : m_accruedList) {
			sum += v;
		}
		m_accruedList.set(0, sum);
		Util.dataLogger().info("System utility records: " + m_accruedList);
		super.doTerminate();
	}

	/* (non-Javadoc)
	 * @see org.sa.rainbow.core.AbstractRainbowRunnable#log(java.lang.String)
	 */
	@Override
	protected void log (String txt) {
		Oracle.instance().writeManagerPanel(m_logger, txt);
	}

	/* (non-Javadoc)
	 * @see org.sa.rainbow.core.AbstractRainbowRunnable#runAction()
	 */
	@Override
	protected void runAction () {
		if (m_beacon != null && m_beacon.periodElapsed()) {
			// computes instantaneous system utility every model evaluation period
			double util = ((AdaptationManager )Oracle.instance().adaptationManager()).computeSystemInstantUtility();
			StringBuffer umsg = new StringBuffer();
			umsg.append("IU: ").append(util);
			Util.dataLogger().info(umsg.toString());
			m_logger.info(umsg.toString());
			m_accruedList.add(util);
			m_beacon.mark();
		}
		if (Rainbow.inSimulation()) {  // Gauge Coordinator NOT used
			// fetch info from monitoring infra, update model with fresh properties
			if (!m_sim.isTerminated()) {
				for (Entry<String,Object> e : m_sim.getChangedProperties().entrySet()) {
					m_model.updateProperty(e.getKey(), e.getValue());
				}
			}
		} else {
			// check cloud health with SystemDelegate, possibly starting RainbowDelegates
			m_sys.checkCloudHealth();
			// coordinate some gauges
			m_gaugeCoord.createGauges();
			if (m_sys.allLocationsReady()) {
				m_gaugeCoord.configureGauges();
			}
			m_gaugeCoord.checkGauges();
		}
	}

	/* (non-Javadoc)
	 * @see org.sa.rainbow.model.manager.IModelManager#queryElementProperty(java.lang.String, org.acmestudio.acme.element.IAcmeElementInstance)
	 */
	public Object queryElementProperty (String propName, IAcmeElementInstance<?,?> element) {
		// TODO: translate element and property name to some system-level identifier
		String sysId = element.getQualifiedName() + "." + propName;

		TargetSystem sys = Oracle.instance().targetSystem();
		return sys.queryProperty(sysId);
	}

	/* (non-Javadoc)
	 * @see org.sa.rainbow.model.manager.IModelManager#availableServices(org.acmestudio.acme.element.IAcmeElementType)
	 */
	public int availableServices (IAcmeElementType<?,?> type) {
		Map<String,String> filters = new HashMap<String,String>();
		return findServices(type, filters).size();
	}

	/* (non-Javadoc)
	 * @see org.sa.rainbow.model.manager.IModelManager#findServices(org.acmestudio.acme.element.IAcmeElementType, java.util.Map)
	 */
	public Set<IAcmeElementInstance<?,?>> findServices (IAcmeElementType<?,?> type, Map<String,String> filters) {
		Set<IAcmeElementInstance<?,?>> services = new HashSet<IAcmeElementInstance<?,?>>();
		// Find available services matching criteria thru env't model.
		filters.put(Model.PROPKEY_ARCH_ENABLED, String.valueOf(false));
		IAcmeModel acmeModel = (IAcmeModel )m_model.getAcmeModel();
		// 1. iterate through AcmeSystems
		Set<? extends IAcmeSystem> systems = acmeModel.getSystems();
		for (IAcmeSystem sys : systems) {
			// 2. iterate thru children objects within the AcmeSystem
			List<IAcmeObject> children = sys.getChildren();
			for (IAcmeObject acmeObj : children) {
				if (acmeObj instanceof IAcmeElementInstance<?,?>) {
					// 3. for each child that is an element instance, check for type match
					IAcmeElementInstance<?,?> inst = (IAcmeElementInstance<?,?> )acmeObj;
					if (ModelHelper.declaresType(inst, type)) {  // found an instance
						// 4. finally, further check for property matches using filters
						boolean allMatch = true;  // assume all true
						for (String key : filters.keySet()) {
							String val = filters.get(key);
							IAcmeProperty prop = inst.getProperty(key);
							if (! val.equals(prop.getValue().toString())) {
								// oh no! we have a mismatch!
								allMatch = false;
								break;  // proceed to next child, if available
							}
						}
						if (allMatch) {  // found an instance
							services.add(inst);
						}
					}
				}
			}
		}
		if (services.isEmpty()) {
			// find directly by querying system
			TargetSystem sys = Oracle.instance().targetSystem();
			Set<?> sysServices = sys.findServices(type.getQualifiedName(), filters);
			// TODO: translate found system services to arch-level element instances
			for (Object o : sysServices) {
				IAcmeElementInstance<?,?> inst = (IAcmeElementInstance<?,?> )
					acmeModel.findNamedObject(acmeModel, o.toString());
				if (inst != null) {
					services.add(inst);
				}
			}
		}
		m_logger.trace("FindServices called! returned service set: " + services.toString());
		return services;
	}

	private IRainbowEventListener healthListener (String id) {
		return new RainbowEventAdapter(id) {
			@EventTypeFilter(eventTypes={
			})
			public void onMessage (IRainbowMessage msg) {
				IProtocolAction action = IProtocolAction.valueOf((String )msg.getProperty(IProtocolAction.ACTION));
				switch (action) {
				}
			}
		};
	}

}
