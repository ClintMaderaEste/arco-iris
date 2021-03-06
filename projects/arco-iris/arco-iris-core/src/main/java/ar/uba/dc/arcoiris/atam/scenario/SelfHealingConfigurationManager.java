package ar.uba.dc.arcoiris.atam.scenario;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Level;
import org.sa.rainbow.core.Oracle;
import org.sa.rainbow.util.RainbowLogger;
import org.sa.rainbow.util.RainbowLoggerFactory;

import ar.uba.dc.arcoiris.atam.scenario.model.Environment;
import ar.uba.dc.arcoiris.atam.scenario.model.Stimulus;
import ar.uba.dc.arcoiris.repository.SelfHealingConfigurationChangeListener;
import ar.uba.dc.arcoiris.repository.SelfHealingConfigurationRepository;
import ar.uba.dc.arcoiris.selfhealing.SelfHealingScenario;
import ar.uba.dc.arcoiris.selfhealing.repair.SpecificRepairStrategies;

public class SelfHealingConfigurationManager implements SelfHealingConfigurationChangeListener {

	/** Holds a list of Scenarios keyed by their corresponding stimulus name */
	private Map<Stimulus, List<SelfHealingScenario>> scenariosMap;

	/**
	 * Holds a list of Stimulus keyed by the property involved in the Response Measure Used for simulation only
	 */
	private Map<String, List<Stimulus>> stimulusByPropertyMap;

	private final SelfHealingConfigurationRepository selfHealingConfigurationRepository;

	private int maxPriority;

	private static RainbowLogger logger = RainbowLoggerFactory.logger(SelfHealingConfigurationManager.class);

	/**
	 * Constructor
	 * 
	 * @param selfHealingConfigurationRepository
	 * @param acmeModel
	 */
	public SelfHealingConfigurationManager(SelfHealingConfigurationRepository selfHealingConfigurationRepository) {
		super();
		this.selfHealingConfigurationRepository = selfHealingConfigurationRepository;
		this.loadScenarios();
		this.selfHealingConfigurationRepository.register(this);
	}

	/**
	 * Constructor used for testing in order to avoid using mocks.
	 * 
	 * @param maxPriority
	 */
	SelfHealingConfigurationManager(int maxPriority) {
		super();
		this.selfHealingConfigurationRepository = null;
		this.maxPriority = maxPriority;
	}

	/**
	 * This method is only useful when Arco Iris is "in simulation"
	 */
	public List<Stimulus> getStimuli(String qualifiedPropertyName) {
		List<Stimulus> stimulusPerProperty = this.stimulusByPropertyMap.get(qualifiedPropertyName);
		return stimulusPerProperty == null ? new ArrayList<Stimulus>(0) : stimulusPerProperty;
	}

	public List<SelfHealingScenario> findBrokenScenarios(Stimulus stimulus) {
		log(Level.INFO, "Finding broken scenarios...");

		List<SelfHealingScenario> brokenScenarios = new ArrayList<SelfHealingScenario>();
		List<SelfHealingScenario> scenariosWithStimulus = this.getScenarios(stimulus);

		for (SelfHealingScenario scenario : scenariosWithStimulus) {
			log(Level.INFO, "Is scenario " + scenario.getName() + " broken?");
			String scenarioStatus = " --> PASSED";
			if (Oracle.instance().scenarioBrokenDetector4CurrentSystemState().isBroken(scenario)) {
				scenarioStatus = " --> BROKEN";
				brokenScenarios.add(scenario);
			}
			log(Level.INFO, scenario.getName() + scenarioStatus + "!");
		}
		log(Level.INFO, "END Finding broken scenarios!");
		return brokenScenarios;
	}

	public List<SelfHealingScenario> getScenarios(Stimulus stimulus) {
		List<SelfHealingScenario> result = new ArrayList<SelfHealingScenario>(this.scenariosMap.get(stimulus));
		List<SelfHealingScenario> scenariosValidForAnyStimulus = this.scenariosMap.get(Stimulus.ANY);
		if (scenariosValidForAnyStimulus != null) {
			result.addAll(scenariosValidForAnyStimulus);
		}
		return result;
	}

	public Collection<SelfHealingScenario> getEnabledScenarios() {
		return this.selfHealingConfigurationRepository.getEnabledScenarios();
	}

	public int getMaxPriority() {
		return maxPriority;
	}

	public Collection<Environment> getAllEnvironments() {
		return this.selfHealingConfigurationRepository.getAllEnvironments();
	}

	public Environment getAnyEnvironment() {
		return this.selfHealingConfigurationRepository.getAnyEnvironment();
	}

	/**
	 * Reload in-memory information about Scenarios every time the underlying configuration changes.
	 */
	@Override
	public void selfHealingConfigurationHasChanged() {
		logger.info("Reloading in-memory information about Self Healing Scenarios...");
		this.loadScenarios();
	}

	protected void log(Level level, String txt, Throwable... t) {
		Oracle.instance().writeEvaluatorPanel(logger, level, txt, t);
	}

	/**
	 * This method retrieves the enabled scenarios provided by
	 * {@link SelfHealingConfigurationRepository#getEnabledScenarios()} and puts them on a map keyed by its stimulus.
	 */
	private Collection<SelfHealingScenario> loadScenarios() {
		Collection<SelfHealingScenario> scenarios = this.selfHealingConfigurationRepository.getEnabledScenarios();

		this.scenariosMap = new HashMap<Stimulus, List<SelfHealingScenario>>();
		this.stimulusByPropertyMap = new HashMap<String, List<Stimulus>>();

		for (SelfHealingScenario currentScenario : scenarios) {
			Stimulus stimulus = currentScenario.getStimulus();

			loadStimulusByProperty(currentScenario, stimulus);

			List<SelfHealingScenario> scenarioList;
			if (this.scenariosMap.containsKey(stimulus)) {
				scenarioList = this.scenariosMap.get(stimulus);
			} else {
				scenarioList = new ArrayList<SelfHealingScenario>();
				this.scenariosMap.put(stimulus, scenarioList);
			}
			scenarioList.add(currentScenario);

			this.maxPriority = Math.max(currentScenario.getPriority(), this.maxPriority);

			// if the user does not specify any repair strategy, we consider all available ones
			if (currentScenario.getRepairStrategies().useAllRepairStrategies()) {
				String[] allRepairStrategies = Oracle.instance().stitchLoader().getAllStrategyNames()
						.toArray(new String[] {});
				currentScenario.setRepairStrategies(new SpecificRepairStrategies(allRepairStrategies));
			}
		}

		return scenarios;
	}

	private void loadStimulusByProperty(SelfHealingScenario currentScenario, Stimulus stimulus) {
		List<Stimulus> stimulusPerScenarioList;
		String responseMeasureProperty = currentScenario.getResponseMeasure().getConstraint()
				.getFullyQualifiedPropertyName();

		if (this.stimulusByPropertyMap.containsKey(responseMeasureProperty)) {
			stimulusPerScenarioList = this.stimulusByPropertyMap.get(responseMeasureProperty);
		} else {
			stimulusPerScenarioList = new ArrayList<Stimulus>();
			stimulusByPropertyMap.put(responseMeasureProperty, stimulusPerScenarioList);
		}
		stimulusPerScenarioList.add(stimulus);
	}
}
