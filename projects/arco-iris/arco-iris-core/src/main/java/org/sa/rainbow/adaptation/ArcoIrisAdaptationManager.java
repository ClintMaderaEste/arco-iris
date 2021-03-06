package org.sa.rainbow.adaptation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Level;
import org.sa.rainbow.adaptation.executor.Executor;
import org.sa.rainbow.core.AbstractRainbowRunnable;
import org.sa.rainbow.core.Oracle;
import org.sa.rainbow.core.Rainbow;
import org.sa.rainbow.health.Beacon;
import org.sa.rainbow.health.IRainbowHealthProtocol;
import org.sa.rainbow.model.UtilityPreferenceDescription.UtilityAttributes;
import org.sa.rainbow.scenario.model.ArcoIrisModel;
import org.sa.rainbow.stitch.core.Strategy;
import org.sa.rainbow.stitch.core.UtilityFunction;
import org.sa.rainbow.stitch.visitor.Stitch;
import org.sa.rainbow.util.RainbowLogger;
import org.sa.rainbow.util.RainbowLoggerFactory;
import org.sa.rainbow.util.StopWatch;
import org.sa.rainbow.util.Util;

import ar.uba.dc.arcoiris.atam.scenario.SelfHealingConfigurationManager;
import ar.uba.dc.arcoiris.atam.scenario.model.Environment;
import ar.uba.dc.arcoiris.qa.Concern;
import ar.uba.dc.arcoiris.selfhealing.ScenarioBrokenDetector;
import ar.uba.dc.arcoiris.selfhealing.SelfHealingScenario;
import ar.uba.dc.arcoiris.selfhealing.priority.DefaultScenarioRelativePriorityAssigner;
import ar.uba.dc.arcoiris.selfhealing.priority.ScenarioRelativePriorityAssigner;
import ar.uba.dc.arcoiris.selfhealing.score.DefaultScenarioScoreAssigner;
import ar.uba.dc.arcoiris.selfhealing.score.ScenarioBrokenDetector4CurrentSystemState;
import ar.uba.dc.arcoiris.selfhealing.score.ScenarioBrokenDetector4StrategyScoring;
import ar.uba.dc.arcoiris.selfhealing.score.ScenarioScoreAssigner;

/**
 * The Rainbow Adaptation Engine... <br>
 * NOTE: This class is based on the original <code>AdaptationManager</code> code since it is final and thus, it cannot
 * be extended.<br>
 */
public class ArcoIrisAdaptationManager extends AbstractRainbowRunnable {

	public enum Mode {
		SERIAL, MULTI_PRONE
	};

	public static final String NAME = "Arco Iris Adaptation Manager";

	public static final double FAILURE_RATE_THRESHOLD = 0.95;

	public static final double MIN_UTILITY_THRESHOLD = 0.40;

	private static double m_minUtilityThreshold = 0.0;

	public static final long FAILURE_EFFECTIVE_WINDOW = 2000 /* ms */;

	public static final long FAILURE_WINDOW_CHUNK = 1000 /* ms */;

	/**
	 * The prefix to be used by the strategy which takes a "leap" by achieving a similar adaptation that would have
	 * taken multiple increments of the non-leap version, but at a potential "cost" in non-dire scenarios.
	 */
	public static final String LEAP_STRATEGY_PREFIX = "Leap-";
	/**
	 * The prefix to represent the corresponding multi-step strategy of the leap-version strategy.
	 */
	public static final String MULTI_STRATEGY_PREFIX = "Multi-";

	protected static RainbowLogger m_logger = null;

	private static List<SelfHealingScenario> currentBrokenScenarios;

	private static ScenarioBrokenDetector4CurrentSystemState scenarioBrokenDetector4CurrentSystemState;

	private static ScenarioScoreAssigner scenarioScoreAssigner;

	private final SelfHealingConfigurationManager selfHealingConfigurationManager;

	private ScenarioRelativePriorityAssigner scenarioRelativePriorityAssigner;

	private final Mode m_mode = Mode.SERIAL;

	private ArcoIrisModel m_model;

	private boolean m_adaptNeeded = false; // treat as synonymous with constraint being violated

	private boolean m_adaptEnabled = true; // by default, we adapt

	private List<Stitch> m_repertoire;

	private SortedMap<String, UtilityFunction> m_utils;

	private List<Strategy> m_pendingStrategies;

	// track history
	private String m_historyTrackUtilName;

	private Map<String, int[]> m_historyCnt;

	private Map<String, Beacon> m_failTimer;

	/**
	 * Default constructor.
	 */
	public ArcoIrisAdaptationManager(SelfHealingConfigurationManager selfHealingConfigurationManager) {
		super(NAME);
		m_logger = RainbowLoggerFactory.logger(getClass());
		scenarioBrokenDetector4CurrentSystemState = Oracle.instance().scenarioBrokenDetector4CurrentSystemState();
		scenarioScoreAssigner = new DefaultScenarioScoreAssigner();
		currentBrokenScenarios = new ArrayList<SelfHealingScenario>();
		this.selfHealingConfigurationManager = selfHealingConfigurationManager;
		scenarioRelativePriorityAssigner = new DefaultScenarioRelativePriorityAssigner(
				this.selfHealingConfigurationManager);
		this.m_model = (ArcoIrisModel) Oracle.instance().rainbowModel();
		this.m_utils = new TreeMap<String, UtilityFunction>();
		this.m_pendingStrategies = new ArrayList<Strategy>();
		this.m_historyTrackUtilName = Rainbow.property(Rainbow.PROPKEY_TRACK_STRATEGY);
		if (this.m_historyTrackUtilName != null) {
			this.m_historyCnt = new HashMap<String, int[]>();
			this.m_failTimer = new HashMap<String, Beacon>();
		}
		String thresholdStr = Rainbow.property(Rainbow.PROPKEY_UTILITY_MINSCORE_THRESHOLD);
		if (thresholdStr == null) {
			m_minUtilityThreshold = MIN_UTILITY_THRESHOLD;
		} else {
			m_minUtilityThreshold = Double.valueOf(thresholdStr);
		}

		for (String k : Rainbow.instance().preferenceDesc().utilities.keySet()) {
			UtilityAttributes ua = Rainbow.instance().preferenceDesc().utilities.get(k);
			UtilityFunction uf = new UtilityFunction(k, ua.label, ua.mapping, ua.desc, ua.values);
			m_utils.put(k, uf);
		}

		this.m_repertoire = Oracle.instance().stitchLoader().getParsedStitches();
	}

	public static boolean isConcernStillBroken(String concernString) {
		try {
			Concern concern = Concern.valueOf(concernString);
			doLog(Level.INFO, "Is Concern " + concern + " Still Broken?");

			boolean result = false;
			for (SelfHealingScenario scenario : currentBrokenScenarios) {
				if (scenario.getConcern().equals(concern)
						&& scenarioBrokenDetector4CurrentSystemState.isBroken(scenario)) {
					result = true;
					break;
				}
			}
			doLog(Level.INFO, "Concern " + concern + (result == true ? " Still Broken!" : " Not Broken Anymore!"));
			return result;
		} catch (NullPointerException e) {
			doLog(Level.ERROR, "Concern not specified");
			throw e;
		} catch (IllegalArgumentException e) {
			doLog(Level.ERROR, "Concern " + concernString + " does not exist");
			throw e;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sa.rainbow.core.IDisposable#dispose()
	 */
	public void dispose() {
		for (Stitch stitch : m_repertoire) {
			stitch.dispose();
		}
		m_repertoire.clear();
		m_utils.clear();
		m_pendingStrategies.clear();
		if (m_historyTrackUtilName != null) {
			m_historyCnt.clear();
			m_failTimer.clear();
			m_historyCnt = null;
			m_failTimer = null;
		}

		// null-out data members
		m_repertoire = null;
		m_utils = null;
		m_pendingStrategies = null;
		m_historyTrackUtilName = null;
		m_model = null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sa.rainbow.core.AbstractRainbowRunnable#log(java.lang.String)
	 */
	@Override
	protected void log(String txt) {
		doLog(Level.INFO, txt);
	}

	protected static void doLog(Level level, String txt, Throwable... t) {
		Oracle.instance().writeEnginePanel(m_logger, level, txt, t);
	}

	public boolean adaptationEnabled() {
		return m_adaptEnabled;
	}

	public void setAdaptationEnabled(boolean b) {
		m_adaptEnabled = b;
	}

	/**
	 * Marks a flag to trigger the Adaptation Engine to go to work finding a repair.
	 */
	public void triggerAdaptation() {
		m_adaptNeeded = true;
	}

	public boolean adaptationInProgress() {
		return m_adaptNeeded;
	}

	/**
	 * Removes a Strategy from the list of pending strategies, marking it as being completed (doesn't incorporate
	 * outcome).
	 * 
	 * @param strategy
	 *            the strategy to mark as being executed.
	 */
	public void markStrategyExecuted(Strategy strategy) {
		if (m_pendingStrategies.contains(strategy)) {
			m_pendingStrategies.remove(strategy);
			doLog(Level.TRACE, "Strategy " + strategy.getName() + " outcome: " + strategy.outcome());
			String s = strategy.getName() + ";" + strategy.outcome();
			Util.dataLogger().info(IRainbowHealthProtocol.DATA_ADAPTATION_STRATEGY + s);
			tallyStrategyOutcome(strategy);
		}
		if (m_pendingStrategies.size() == 0) {
			Util.dataLogger().info(IRainbowHealthProtocol.DATA_ADAPTATION_END);
			// reset adaptation flags
			m_adaptNeeded = false;
			m_model.clearConstraintViolated();
		}
	}

	/**
	 * Computes instantaneous utility of target system given current conditions.
	 * 
	 * @return double the instantaneous utility of current conditions
	 */
	public double computeSystemInstantUtility() {
		Map<String, Double> weights = Rainbow.instance().preferenceDesc().weights.get(Rainbow
				.property(Rainbow.PROPKEY_SCENARIO));
		double score = 0.0;
		for (String k : m_utils.keySet()) {
			// find the applicable utility function
			UtilityFunction u = m_utils.get(k);
			Object condVal = m_model.getProperty(u.mapping());
			// now compute the utility, apply weight, and accumulate to sum
			// but only if weight is defined and the value is a Double
			if (condVal != null && condVal instanceof Double && weights.containsKey(k)) {
				if (m_logger.isTraceEnabled())
					doLog(Level.TRACE, "Avg value of prop: " + u.mapping() + " == " + condVal);
				score += weights.get(k) * u.f(((Double) condVal).doubleValue());
			}
		}
		return score;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sa.rainbow.core.AbstractRainbowRunnable#runAction()
	 */
	@Override
	protected void runAction() {
		if (m_adaptEnabled && m_adaptNeeded) {
			if ((m_mode == Mode.SERIAL && m_pendingStrategies.size() == 0) || m_mode == Mode.MULTI_PRONE) {
				// in serial mode, only do adaptation if no strategy is pending
				// in multi-prone mode, just do adaptation
				Util.dataLogger().info(IRainbowHealthProtocol.DATA_ADAPTATION_BEGIN);
				doAdaptation();
			}
		}
	}

	/**
	 * For JUnit testing, used to set a stopwatch object used to time duration.
	 */
	StopWatch _stopWatchForTesting = null;

	/**
	 * For JUnit testing, allows fetching the strategy repertoire. NOT for public use!
	 * 
	 * @return list of Stitch objects loaded at initialization from stitch file.
	 */
	List<Stitch> _retrieveRepertoireForTesting() {
		return m_repertoire;
	}

	/**
	 * For JUnit testing, allows fetching the utility objects. NOT for public use!
	 * 
	 * @return map of utility identifiers to functions.
	 */
	Map<String, UtilityFunction> _retrieveUtilityProfilesForTesting() {
		return m_utils;
	}

	private void doAdaptation() {
		// nothing to do, avoid doing Rainbow adaptation
	}

	/**
	 * Algorithm:
	 * <ol>
	 * <li>Iterate through repertoire searching for enabled strategies, where "enabled" means applicable to current
	 * system condition. NOTE: A Strategy is "applicable" iff the conditions of applicability of the root tactic is
	 * true.
	 * <li>Calculate scores of the enabled strategies (this involves evaluating the meta-information of the tactics in
	 * each strategy).
	 * <li>Select and execute the highest scoring strategy
	 */
	public void triggerAdaptation(List<SelfHealingScenario> brokenScenarios) {
		m_adaptNeeded = true;
		currentBrokenScenarios = brokenScenarios;

		doLog(Level.INFO, "Adaptation triggered, let's begin!");
		if (_stopWatchForTesting != null)
			_stopWatchForTesting.start();

		Set<String> candidateStrategies = collectCandidateStrategiesNames(brokenScenarios);

		Environment currentSystemEnvironment = detectCurrentSystemEnvironment(this.m_model);

		// We don't want the "simulated" system utility to be worse than the current real one.
		doLog(Level.INFO, "Computing Current System Utility...");
		double maxStrategyScore = computeArcoIrisSystemUtility(currentSystemEnvironment,
				scenarioBrokenDetector4CurrentSystemState);

		doLog(Level.INFO, "Current System Utility (Score to improve): " + maxStrategyScore);

		Strategy selectedStrategy = null;

		for (Stitch stitch : m_repertoire) {
			if (!stitch.script.isApplicableForModel(m_model.getAcmeModel())) {
				if (m_logger.isDebugEnabled())
					doLog(Level.DEBUG, "x. skipping " + stitch.script.getName());
				continue; // skip checking this script
			}

			for (Strategy currentStrategy : stitch.script.strategies) {
				// TODO FailureRate is not working for us, since we need to configure first the property
				// "customize.utility.trackStrategy" in rainbow.properties
				boolean isNotCandidate = !candidateStrategies.contains(currentStrategy.getName());
				if (isNotCandidate || (getFailureRate(currentStrategy) > FAILURE_RATE_THRESHOLD)) {
					String cause = isNotCandidate ? "not selected in broken scenarios"
							: "failure rate threshold reached";
					doLog(Level.INFO, "Strategy does not apply (" + cause + "): " + currentStrategy.getName());
					continue; // don't consider this Strategy
				}
				doLog(Level.INFO, "Evaluating strategy " + currentStrategy.getName() + "...");

				doLog(Level.INFO, "Scoring " + currentStrategy.getName() + "...");
				double strategyScore = computeArcoIrisSystemUtility(currentSystemEnvironment,
						new ScenarioBrokenDetector4StrategyScoring(m_model, currentStrategy));
				doLog(Level.INFO, "Score for strategy " + currentStrategy.getName() + ": " + strategyScore);

				Map<String, Double> weightsForRainbow = currentSystemEnvironment.getWeightsForRainbow();

				if (strategyScore > maxStrategyScore) {
					maxStrategyScore = strategyScore;
					selectedStrategy = currentStrategy;
					doLog(Level.INFO, "Current best strategy: " + selectedStrategy.getName());
				} else if (strategyScore == maxStrategyScore) {
					int improvesRainbowSystemUtility = compareRainbowSystemUtility(currentStrategy, selectedStrategy,
							weightsForRainbow);
					if (improvesRainbowSystemUtility > 0) {
						selectedStrategy = currentStrategy;
					} else if (improvesRainbowSystemUtility == 0 && selectedStrategy != null
							&& getFailureRate(currentStrategy) < getFailureRate(selectedStrategy)) {
						selectedStrategy = currentStrategy;
					}
				}
			}
		}

		if (_stopWatchForTesting != null)
			_stopWatchForTesting.stop();
		if (selectedStrategy != null) {
			doLog(Level.INFO, "Selected strategy: " + selectedStrategy.getName() + "!!!");
			// strategy args removed...
			Object[] args = new Object[0];
			m_pendingStrategies.add(selectedStrategy);
			((Executor) Oracle.instance().strategyExecutor()).enqueueStrategy(selectedStrategy, args);
			doLog(Level.TRACE, "<< Adaptation cycle awaits Executor...");
		} else {
			Util.dataLogger().info(IRainbowHealthProtocol.DATA_ADAPTATION_END);
			doLog(Level.INFO, "NO applicable strategy, adaptation cycle ended.");
			m_adaptNeeded = false;
			m_model.clearConstraintViolated();
		}
	}

	/**
	 * Compares <code>currentStrategy</code> and <code>selectedStrategy</code> Rainbow System Utilities
	 * 
	 * @return the value 0 if Rainbow's System Utility for currentStrategy is equals to selectedStrategy's. A value less
	 *         than 0 if it's less and a value greater than 0 if it's greater.
	 */
	private int compareRainbowSystemUtility(Strategy currentStrategy, Strategy selectedStrategy,
			Map<String, Double> weightsForRainbow) {
		Double currentStrategyRaibowSystemUtility = computeRaibowSystemUtilityAfterStrategy(currentStrategy,
				weightsForRainbow);
		double selectedStrategyRainbowSystemUtility;
		if (selectedStrategy == null) {
			selectedStrategyRainbowSystemUtility = computeSystemInstantUtility();
		} else {
			selectedStrategyRainbowSystemUtility = computeRaibowSystemUtilityAfterStrategy(selectedStrategy,
					weightsForRainbow);
		}
		int improveRainbowSystemUtility = currentStrategyRaibowSystemUtility
				.compareTo(selectedStrategyRainbowSystemUtility);
		return improveRainbowSystemUtility;
	}

	private Set<String> collectCandidateStrategiesNames(List<SelfHealingScenario> brokenScenarios) {
		Set<String> candidateStrategiesNames = new HashSet<String>();
		for (SelfHealingScenario brokenScenario : brokenScenarios) {
			candidateStrategiesNames.addAll(brokenScenario.getRepairStrategies().getRepairStrategiesNames());
		}
		return candidateStrategiesNames;
	}

	/**
	 * Detect the current system environment considering all the environments available in the system. The first
	 * environment that holds is returned, or the wildcard "ANY" is returned in the event that none of the above held.
	 * <p>
	 * <b>NOTE: We assume that the environments' conditions are mutually exclusive among each other.</b>
	 * 
	 * @return the current system enviroment
	 */
	private Environment detectCurrentSystemEnvironment(ArcoIrisModel arcoIrisModel) {
		Collection<Environment> environments = this.selfHealingConfigurationManager.getAllEnvironments();
		for (Environment environment : environments) {
			if (environment.holds(arcoIrisModel)) {
				doLog(Level.INFO, "Current environment: " + environment.getName());
				return environment;
			}
		}
		doLog(Level.INFO, "Until further notice, any environment will apply in the current state of the system");
		return this.selfHealingConfigurationManager.getAnyEnvironment();
	}

	private double computeArcoIrisSystemUtility(Environment currentSystemEnvironment,
			ScenarioBrokenDetector scenarioBrokenDetector) {
		double strategyScore = 0L;
		Collection<SelfHealingScenario> scenarios = this.selfHealingConfigurationManager.getEnabledScenarios();
		for (SelfHealingScenario scenario : scenarios) {
			double scenarioRelativePriority = scenarioRelativePriorityAssigner.relativePriority(scenario);
			double scenarioScore = scenarioScoreAssigner.scenarioScore(scenarioBrokenDetector, scenario,
					currentSystemEnvironment, scenarioRelativePriority, m_model, scenario.getPropertyMapping());
			strategyScore = strategyScore + scenarioScore;
		}
		return strategyScore;
	}

	/**
	 * Rainbow's algorithm for calculating the score of a strategy
	 * 
	 * @return the score of the strategy calculated by Rainbow
	 */
	private double computeRaibowSystemUtilityAfterStrategy(Strategy strategy, Map<String, Double> weights) {
		double[] conds = null;
		SortedMap<String, Double> aggAtt = strategy.computeAggregateAttributes();
		// add the strategy failure history as another attribute
		accountForStrategyHistory(aggAtt, strategy);
		String s = strategy.getName() + aggAtt;
		Util.dataLogger().info(IRainbowHealthProtocol.DATA_ADAPTATION_STRATEGY_ATTR + s);
		doLog(Level.DEBUG, "aggAttr: " + s);
		/*
		 * compute utility values from attributes that combines values representing current condition, then accumulate
		 * the weighted utility sum
		 */
		double[] items = new double[aggAtt.size()];
		// double[] itemsPred = new double[aggAtt.size()];
		conds = new double[aggAtt.size()];
		// if (condsPred == null)
		// condsPred = new double[aggAtt.size()];
		int i = 0;
		double score = 0.0;
		// double scorePred = 0.0; // score based on predictions
		for (String k : aggAtt.keySet()) {
			double v = aggAtt.get(k);
			// find the applicable utility function
			UtilityFunction u = m_utils.get(k);
			Object condVal = null;
			// Object condValPred = null;
			// add attribute value from CURRENT condition to accumulated agg value
			condVal = m_model.getProperty(u.mapping());
			items[i] = v;
			if (condVal != null && condVal instanceof Double) {
				if (m_logger.isTraceEnabled())
					doLog(Level.TRACE, "Avg value of prop: " + u.mapping() + " == " + condVal);
				conds[i] = ((Double) condVal).doubleValue();
				items[i] += conds[i];
			}
			// now compute the utility, apply weight, and accumulate to sum
			score += weights.get(k) * u.f(items[i]);
			++i;
		}

		// log this
		s = Arrays.toString(items);
		if (score < m_minUtilityThreshold) {
			// utility score too low, don't consider for adaptation
			doLog(Level.TRACE, "score " + score + " below threshold, discarding: " + s);
		}
		Util.dataLogger().info(IRainbowHealthProtocol.DATA_ADAPTATION_STRATEGY_ATTR2 + s);
		doLog(Level.DEBUG, "aggAtt': " + s);
		return score;
	}

	private void tallyStrategyOutcome(Strategy s) {
		if (m_historyTrackUtilName == null)
			return;

		String name = s.getName();
		// mark timer of failure, if applicable
		Beacon timer = m_failTimer.get(name);
		if (timer == null) {
			timer = new Beacon();
			m_failTimer.put(name, timer);
		}
		// get the stats array for this strategy
		int[] stat = m_historyCnt.get(name);
		if (stat == null) {
			stat = new int[CNT_I];
			stat[I_RUN] = 0;
			stat[I_SUCCESS] = 0;
			stat[I_FAIL] = 0;
			stat[I_OTHER] = 0;
			m_historyCnt.put(name, stat);
		}
		// tally outcome counts
		++stat[I_RUN];
		switch (s.outcome()) {
		case SUCCESS:
			++stat[I_SUCCESS];
			break;
		case FAILURE:
			++stat[I_FAIL];
			timer.mark();
			break;
		default:
			++stat[I_OTHER];
			break;
		}
		String str = name + Arrays.toString(stat);
		doLog(Level.TRACE, "History: " + str);
		Util.dataLogger().info(IRainbowHealthProtocol.DATA_ADAPTATION_STAT + str);
	}

	private void accountForStrategyHistory(Map<String, Double> aggAtt, Strategy s) {
		if (m_historyTrackUtilName == null)
			return;

		if (m_historyCnt.containsKey(s.getName())) {
			// consider failure only
			aggAtt.put(m_historyTrackUtilName, getFailureRate(s));
		} else {
			// consider no failure
			aggAtt.put(m_historyTrackUtilName, 0.0);
		}
	}

	private double getFailureRate(Strategy s) {
		double rv = 0.0;
		if (m_historyTrackUtilName == null)
			return rv;

		int[] stat = m_historyCnt.get(s.getName());
		if (stat != null) {
			Beacon timer = m_failTimer.get(s.getName());
			// as time passes, historical failures in strategy become less severe
			double factor = 1.0;
			long failTimeDelta = timer.elapsedTime() - FAILURE_EFFECTIVE_WINDOW;
			if (failTimeDelta > 0) {
				factor = FAILURE_WINDOW_CHUNK * 1.0 / failTimeDelta;
			}
			rv = factor * stat[I_FAIL] / stat[I_RUN];
		}
		return rv;
	}

	private static final int I_RUN = 0;
	private static final int I_SUCCESS = 1;
	private static final int I_FAIL = 2;
	private static final int I_OTHER = 3;
	private static final int CNT_I = 4;
}
