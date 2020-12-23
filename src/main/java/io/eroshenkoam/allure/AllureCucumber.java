package io.eroshenkoam.allure;

import cucumber.api.HookTestStep;
import cucumber.api.HookType;
import cucumber.api.PendingException;
import cucumber.api.PickleStepTestStep;
import cucumber.api.Result;
import cucumber.api.TestCase;
import cucumber.api.event.ConcurrentEventListener;
import cucumber.api.event.EmbedEvent;
import cucumber.api.event.EventHandler;
import cucumber.api.event.EventPublisher;
import cucumber.api.event.TestCaseFinished;
import cucumber.api.event.TestCaseStarted;
import cucumber.api.event.TestSourceRead;
import cucumber.api.event.TestStepFinished;
import cucumber.api.event.TestStepStarted;
import cucumber.api.event.WriteEvent;
import cucumber.runtime.formatter.TestSourcesModelProxy;
import gherkin.ast.Examples;
import gherkin.ast.Feature;
import gherkin.ast.ScenarioDefinition;
import gherkin.ast.ScenarioOutline;
import gherkin.ast.TableRow;
import gherkin.pickles.PickleCell;
import gherkin.pickles.PickleRow;
import gherkin.pickles.PickleTable;
import gherkin.pickles.PickleTag;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.model.FixtureResult;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.Link;
import io.qameta.allure.model.Parameter;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.model.TestResultContainer;
import io.qameta.allure.util.ResultsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.qameta.allure.util.ResultsUtils.createFeatureLabel;
import static io.qameta.allure.util.ResultsUtils.createFrameworkLabel;
import static io.qameta.allure.util.ResultsUtils.createHostLabel;
import static io.qameta.allure.util.ResultsUtils.createLabel;
import static io.qameta.allure.util.ResultsUtils.createLanguageLabel;
import static io.qameta.allure.util.ResultsUtils.createParameter;
import static io.qameta.allure.util.ResultsUtils.createStoryLabel;
import static io.qameta.allure.util.ResultsUtils.createSuiteLabel;
import static io.qameta.allure.util.ResultsUtils.createTestClassLabel;
import static io.qameta.allure.util.ResultsUtils.createThreadLabel;
import static io.qameta.allure.util.ResultsUtils.getStatus;
import static io.qameta.allure.util.ResultsUtils.getStatusDetails;
import static io.qameta.allure.util.ResultsUtils.md5;

public class AllureCucumber implements ConcurrentEventListener {

    private final AllureLifecycle lifecycle;

    private final ConcurrentHashMap<String, String> scenarioUuids = new ConcurrentHashMap<>();
    private final TestSourcesModelProxy testSources = new TestSourcesModelProxy();

    private final ThreadLocal<Feature> currentFeature = new InheritableThreadLocal<>();
    private final ThreadLocal<String> currentFeatureFile = new InheritableThreadLocal<>();
    private final ThreadLocal<TestCase> currentTestCase = new InheritableThreadLocal<>();
    private final ThreadLocal<String> currentContainer = new InheritableThreadLocal<>();
    private final ThreadLocal<Boolean> forbidTestCaseStatusChange = new InheritableThreadLocal<>();

    private final EventHandler<TestSourceRead> featureStartedHandler = this::handleFeatureStartedHandler;
    private final EventHandler<TestCaseStarted> caseStartedHandler = this::handleTestCaseStarted;
    private final EventHandler<TestCaseFinished> caseFinishedHandler = this::handleTestCaseFinished;
    private final EventHandler<TestStepStarted> stepStartedHandler = this::handleTestStepStarted;
    private final EventHandler<TestStepFinished> stepFinishedHandler = this::handleTestStepFinished;
    private final EventHandler<WriteEvent> writeEventHandler = this::handleWriteEvent;
    private final EventHandler<EmbedEvent> embedEventHandler = this::handleEmbedEvent;

    private static final String TXT_EXTENSION = ".txt";
    private static final String TEXT_PLAIN = "text/plain";

    @SuppressWarnings("unused")
    public AllureCucumber() {
        this(Allure.getLifecycle());
    }

    public AllureCucumber(final AllureLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    public void setEventPublisher(final EventPublisher publisher) {
        publisher.registerHandlerFor(TestSourceRead.class, featureStartedHandler);

        publisher.registerHandlerFor(TestCaseStarted.class, caseStartedHandler);
        publisher.registerHandlerFor(TestCaseFinished.class, caseFinishedHandler);

        publisher.registerHandlerFor(TestStepStarted.class, stepStartedHandler);
        publisher.registerHandlerFor(TestStepFinished.class, stepFinishedHandler);

        publisher.registerHandlerFor(WriteEvent.class, writeEventHandler);
        publisher.registerHandlerFor(EmbedEvent.class, embedEventHandler);
    }

    /*
    Event Handlers
     */

    private void handleFeatureStartedHandler(final TestSourceRead event) {
        testSources.addTestSourceReadEvent(event.uri, event);
    }

    private void handleTestCaseStarted(final TestCaseStarted event) {
        currentFeatureFile.set(event.testCase.getUri());
        currentFeature.set(testSources.getFeature(currentFeatureFile.get()));
        currentTestCase.set(event.testCase);
        currentContainer.set(UUID.randomUUID().toString());
        forbidTestCaseStatusChange.set(false);

        final Deque<PickleTag> tags = new LinkedList<>(currentTestCase.get().getTags());

        final Feature feature = currentFeature.get();
        final LabelBuilder labelBuilder = new LabelBuilder(feature, currentTestCase.get(), tags);

        final String name = currentTestCase.get().getName();
        final String featureName = feature.getName();

        final TestResult result = new TestResult()
                .setUuid(getTestCaseUuid(currentTestCase.get()))
                .setHistoryId(getHistoryId(currentTestCase.get()))
                .setFullName(featureName + ": " + name)
                .setName(name)
                .setLabels(labelBuilder.getScenarioLabels())
                .setLinks(labelBuilder.getScenarioLinks());

        final ScenarioDefinition scenarioDefinition =
                testSources.getScenarioDefinition(currentFeatureFile.get(), currentTestCase.get().getLine());
        if (scenarioDefinition instanceof ScenarioOutline) {
            result.setParameters(
                    getExamplesAsParameters((ScenarioOutline) scenarioDefinition, currentTestCase.get())
            );
        }

        final String description = Stream.of(feature.getDescription(), scenarioDefinition.getDescription())
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));

        if (!description.isEmpty()) {
            result.setDescription(description);
        }

        final TestResultContainer resultContainer = new TestResultContainer()
                .setName(String.format("%s: %s", scenarioDefinition.getKeyword(), scenarioDefinition.getName()))
                .setUuid(getTestContainerUuid())
                .setChildren(Collections.singletonList(getTestCaseUuid(currentTestCase.get())));

        lifecycle.scheduleTestCase(result);
        lifecycle.startTestContainer(getTestContainerUuid(), resultContainer);
        lifecycle.startTestCase(getTestCaseUuid(currentTestCase.get()));
    }

    private void handleTestCaseFinished(final TestCaseFinished event) {

        final String uuid = getTestCaseUuid(event.testCase);
        final Optional<StatusDetails> details = getStatusDetails(event.result.getError());
        details.ifPresent(statusDetails -> lifecycle.updateTestCase(
                uuid,
                testResult -> testResult.setStatusDetails(statusDetails)
        ));
        lifecycle.stopTestCase(uuid);
        lifecycle.stopTestContainer(getTestContainerUuid());
        lifecycle.writeTestCase(uuid);
        lifecycle.writeTestContainer(getTestContainerUuid());
    }

    private void handleTestStepStarted(final TestStepStarted event) {
        if (event.testStep instanceof PickleStepTestStep) {
            final PickleStepTestStep pickleStep = (PickleStepTestStep) event.testStep;
            final String stepKeyword = Optional.ofNullable(
                    testSources.getKeywordFromSource(currentFeatureFile.get(), pickleStep.getStepLine())
            ).orElse("UNDEFINED");

            final StepResult stepResult = new StepResult()
                    .setName(String.format("%s %s", stepKeyword, pickleStep.getPickleStep().getText()))
                    .setStart(System.currentTimeMillis());

            lifecycle.startStep(getTestCaseUuid(currentTestCase.get()), getStepUuid(pickleStep), stepResult);

            pickleStep.getStepArgument().stream()
                    .filter(PickleTable.class::isInstance)
                    .findFirst()
                    .ifPresent(table -> createStepParameters(stepResult, (PickleTable) table));
        } else if (event.testStep instanceof HookTestStep) {
            initHook((HookTestStep) event.testStep);
        }
    }

    private void initHook(final HookTestStep hook) {

        final FixtureResult hookResult = new FixtureResult()
                .setName(hook.getCodeLocation())
                .setStart(System.currentTimeMillis());

        if (hook.getHookType() == HookType.Before) {
            lifecycle.startPrepareFixture(getTestContainerUuid(), getHookStepUuid(hook), hookResult);
        } else {
            lifecycle.startTearDownFixture(getTestContainerUuid(), getHookStepUuid(hook), hookResult);
        }

    }

    private void handleTestStepFinished(final TestStepFinished event) {
        if (event.testStep instanceof HookTestStep) {
            handleHookStep(event);
        } else {
            handlePickleStep(event);
        }
    }

    private void handleWriteEvent(final WriteEvent event) {
        lifecycle.addAttachment(
                "Text output",
                TEXT_PLAIN,
                TXT_EXTENSION,
                Objects.toString(event.text).getBytes(StandardCharsets.UTF_8)
        );
    }

    private void handleEmbedEvent(final EmbedEvent event) {
        lifecycle.addAttachment("Screenshot", null, null, new ByteArrayInputStream(event.data));
    }

    /*
    Utility Methods
     */

    private String getTestContainerUuid() {
        return currentContainer.get();
    }

    private String getTestCaseUuid(final TestCase testCase) {
        return scenarioUuids.computeIfAbsent(getHistoryId(testCase), it -> UUID.randomUUID().toString());
    }

    private String getStepUuid(final PickleStepTestStep step) {
        return currentFeature.get().getName() + getTestCaseUuid(currentTestCase.get())
                + step.getPickleStep().getText() + step.getStepLine();
    }

    private String getHookStepUuid(final HookTestStep step) {
        return currentFeature.get().getName() + getTestCaseUuid(currentTestCase.get())
                + step.getHookType().toString() + step.getCodeLocation();
    }

    private String getHistoryId(final TestCase testCase) {
        final String testCaseLocation = testCase.getUri() + ":" + testCase.getLine();
        return md5(testCaseLocation);
    }

    private Status translateTestCaseStatus(final Result testCaseResult) {
        switch (testCaseResult.getStatus()) {
            case FAILED:
                return getStatus(testCaseResult.getError())
                        .orElse(Status.FAILED);
            case PASSED:
                return Status.PASSED;
            case SKIPPED:
            case PENDING:
                return Status.SKIPPED;
            case AMBIGUOUS:
            case UNDEFINED:
            default:
                return null;
        }
    }

    private List<Parameter> getExamplesAsParameters(
            final ScenarioOutline scenarioOutline, final TestCase localCurrentTestCase
    ) {
        final Optional<Examples> examplesBlock =
                scenarioOutline.getExamples().stream()
                        .filter(example -> example.getTableBody().stream()
                                .anyMatch(row -> row.getLocation().getLine() == localCurrentTestCase.getLine())
                        ).findFirst();

        if (examplesBlock.isPresent()) {
            final TableRow row = examplesBlock.get().getTableBody().stream()
                    .filter(example -> example.getLocation().getLine() == localCurrentTestCase.getLine())
                    .findFirst().get();
            return IntStream.range(0, examplesBlock.get().getTableHeader().getCells().size()).mapToObj(index -> {
                final String name = examplesBlock.get().getTableHeader().getCells().get(index).getValue();
                final String value = row.getCells().get(index).getValue();
                return createParameter(name, value);
            }).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private void createStepParameters(final StepResult result, final PickleTable pickleTable) {
        final List<PickleRow> rows = pickleTable.getRows();
        if (Objects.isNull(result.getParameters())) {
            result.setParameters(new ArrayList<>());
        }
        if (!rows.isEmpty()) {
            rows.forEach(dataTableRow -> {
                if (dataTableRow.getCells().size() > 0) {
                    final String name = dataTableRow.getCells().get(0).getValue();
                    final String value = dataTableRow.getCells().stream().skip(1)
                            .map(PickleCell::getValue)
                            .collect(Collectors.joining(" | "));
                    result.getParameters().add(new Parameter().setName(name).setValue(value));
                }
            });
        }
    }

    private void handleHookStep(final TestStepFinished event) {
        final HookTestStep hookStep = (HookTestStep) event.testStep;
        final String uuid = getHookStepUuid(hookStep);
        final FixtureResult fixtureResult = new FixtureResult().setStatus(translateTestCaseStatus(event.result));

        if (!Status.PASSED.equals(fixtureResult.getStatus())) {
            final TestResult testResult = new TestResult().setStatus(translateTestCaseStatus(event.result));
            final StatusDetails statusDetails = getStatusDetails(event.result.getError())
                    .orElseGet(StatusDetails::new);

            final String errorMessage = event.result.getError() == null ? hookStep.getHookType()
                    .name() + " is failed." : hookStep.getHookType()
                    .name() + " is failed: " + event.result.getError().getLocalizedMessage();
            statusDetails.setMessage(errorMessage);

            if (hookStep.getHookType() == HookType.Before) {
                final TagParser tagParser = new TagParser(currentFeature.get(), currentTestCase.get());
                statusDetails
                        .setFlaky(tagParser.isFlaky())
                        .setMuted(tagParser.isMuted())
                        .setKnown(tagParser.isKnown());
                testResult.setStatus(Status.SKIPPED);
                updateTestCaseStatus(testResult.getStatus());
                forbidTestCaseStatusChange.set(true);
            } else {
                testResult.setStatus(Status.BROKEN);
                updateTestCaseStatus(testResult.getStatus());
            }
            fixtureResult.setStatusDetails(statusDetails);
        }

        lifecycle.updateFixture(uuid, result -> result.setStatus(fixtureResult.getStatus())
                .setStatusDetails(fixtureResult.getStatusDetails()));
        lifecycle.stopFixture(uuid);
    }

    private void handlePickleStep(final TestStepFinished event) {

        final Status stepStatus = translateTestCaseStatus(event.result);
        final StatusDetails statusDetails;
        if (event.result.getStatus() == Result.Type.UNDEFINED) {
            updateTestCaseStatus(Status.PASSED);

            statusDetails =
                    getStatusDetails(new PendingException("TODO: implement me"))
                            .orElse(new StatusDetails());
            lifecycle.updateTestCase(getTestCaseUuid(currentTestCase.get()), scenarioResult ->
                    scenarioResult
                            .setStatusDetails(statusDetails));
        } else {
            statusDetails =
                    getStatusDetails(event.result.getError())
                            .orElse(new StatusDetails());
            updateTestCaseStatus(stepStatus);
        }

        if (!Status.PASSED.equals(stepStatus) && stepStatus != null) {
            forbidTestCaseStatusChange.set(true);
        }

        final TagParser tagParser = new TagParser(currentFeature.get(), currentTestCase.get());
        statusDetails
                .setFlaky(tagParser.isFlaky())
                .setMuted(tagParser.isMuted())
                .setKnown(tagParser.isKnown());

        lifecycle.updateStep(getStepUuid((PickleStepTestStep) event.testStep),
                stepResult -> stepResult.setStatus(stepStatus).setStatusDetails(statusDetails));
        lifecycle.stopStep(getStepUuid((PickleStepTestStep) event.testStep));
    }

    private void updateTestCaseStatus(final Status status) {
        if (!forbidTestCaseStatusChange.get()) {
            lifecycle.updateTestCase(getTestCaseUuid(currentTestCase.get()),
                    result -> result.setStatus(status));
        }
    }

    static class LabelBuilder {
        private static final Logger LOGGER = LoggerFactory.getLogger(LabelBuilder.class);
        private static final String COMPOSITE_TAG_DELIMITER = "=";

        private static final String SEVERITY = "@SEVERITY";
        private static final String ISSUE_LINK = "@ISSUE";
        private static final String TMS_LINK = "@TMSLINK";
        private static final String PLAIN_LINK = "@LINK";

        private final List<Label> scenarioLabels = new ArrayList<>();
        private final List<Link> scenarioLinks = new ArrayList<>();

        LabelBuilder(final Feature feature, final TestCase scenario, final Deque<PickleTag> tags) {
            final TagParser tagParser = new TagParser(feature, scenario);

            while (tags.peek() != null) {
                final PickleTag tag = tags.remove();

                final String tagString = tag.getName();

                if (tagString.contains(COMPOSITE_TAG_DELIMITER)) {

                    final String[] tagParts = tagString.split(COMPOSITE_TAG_DELIMITER, 2);
                    if (tagParts.length < 2 || Objects.isNull(tagParts[1]) || tagParts[1].isEmpty()) {
                        // skip empty tags, e.g. '@tmsLink=', to avoid formatter errors
                        continue;
                    }

                    final String tagKey = tagParts[0].toUpperCase();
                    final String tagValue = tagParts[1];

                    // Handle composite named links
                    if (tagKey.startsWith(PLAIN_LINK + ".")) {
                        tryHandleNamedLink(tagString);
                        continue;
                    }

                    switch (tagKey) {
                        case SEVERITY:
                            getScenarioLabels().add(ResultsUtils.createSeverityLabel(tagValue.toLowerCase()));
                            break;
                        case TMS_LINK:
                            getScenarioLinks().add(ResultsUtils.createTmsLink(tagValue));
                            break;
                        case ISSUE_LINK:
                            getScenarioLinks().add(ResultsUtils.createIssueLink(tagValue));
                            break;
                        case PLAIN_LINK:
                            getScenarioLinks().add(ResultsUtils.createLink(null, tagValue, tagValue, null));
                            break;
                        default:
                            LOGGER.warn("Composite tag {} is not supported. adding it as RAW", tagKey);
                            getScenarioLabels().add(getTagLabel(tag));
                            break;
                    }
                } else if (tagParser.isPureSeverityTag(tag)) {
                    getScenarioLabels().add(ResultsUtils.createSeverityLabel(tagString.substring(1)));
                } else if (!tagParser.isResultTag(tag)) {
                    getScenarioLabels().add(getTagLabel(tag));
                }
            }

            final String featureName = feature.getName();
            final String uri = scenario.getUri();

            getScenarioLabels().addAll(Arrays.asList(
                    createHostLabel(),
                    createThreadLabel(),
                    createFeatureLabel(featureName),
                    createStoryLabel(scenario.getName()),
                    createSuiteLabel(featureName),
                    createTestClassLabel(scenario.getName()),
                    createFrameworkLabel("cucumber4jvm"),
                    createLanguageLabel("java"),
                    createLabel("gherkin_uri", uri)
            ));

            featurePackage(uri, featureName)
                    .map(ResultsUtils::createPackageLabel)
                    .ifPresent(getScenarioLabels()::add);
        }

        public List<Label> getScenarioLabels() {
            return scenarioLabels;
        }

        public List<Link> getScenarioLinks() {
            return scenarioLinks;
        }

        private Label getTagLabel(final PickleTag tag) {
            return ResultsUtils.createTagLabel(tag.getName().substring(1));
        }

        /**
         * Handle composite named links.
         *
         * @param tagString Full tag name and value
         */
        private void tryHandleNamedLink(final String tagString) {
            final String namedLinkPatternString = PLAIN_LINK + "\\.(\\w+-?)+=(\\w+(-|_)?)+";
            final Pattern namedLinkPattern = Pattern.compile(namedLinkPatternString, Pattern.CASE_INSENSITIVE);

            if (namedLinkPattern.matcher(tagString).matches()) {
                final String type = tagString.split(COMPOSITE_TAG_DELIMITER)[0].split("[.]")[1];
                final String name = tagString.split(COMPOSITE_TAG_DELIMITER)[1];
                getScenarioLinks().add(ResultsUtils.createLink(null, name, null, type));
            } else {
                LOGGER.warn("Composite named tag {} does not match regex {}. Skipping", tagString,
                        namedLinkPatternString);
            }
        }

        private Optional<String> featurePackage(final String uriString, final String featureName) {
            final Optional<URI> maybeUri = safeUri(uriString);
            if (!maybeUri.isPresent()) {
                return Optional.empty();
            }
            URI uri = maybeUri.get();

            if (!uri.isOpaque()) {
                final URI work = new File("").toURI();
                uri = work.relativize(uri);
            }
            final String schemeSpecificPart = uri.normalize().getSchemeSpecificPart();
            final Stream<String> folders = Stream.of(schemeSpecificPart.replaceAll("\\.", "_").split("/"));
            final Stream<String> name = Stream.of(featureName);
            return Optional.of(Stream.concat(folders, name)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(".")));
        }

        private static Optional<URI> safeUri(final String uri) {
            try {
                return Optional.of(URI.create(uri));
            } catch (Exception e) {
                LOGGER.debug("could not parse feature uri {}", uri, e);
            }
            return Optional.empty();
        }

    }

    static class TagParser {
        private static final String FLAKY = "@FLAKY";
        private static final String KNOWN = "@KNOWN";
        private static final String MUTED = "@MUTED";

        private final Feature feature;
        private final TestCase scenario;

        TagParser(final Feature feature, final TestCase scenario) {
            this.feature = feature;
            this.scenario = scenario;
        }

        public boolean isFlaky() {
            return getStatusDetailByTag(FLAKY);
        }

        public boolean isMuted() {
            return getStatusDetailByTag(MUTED);
        }

        public boolean isKnown() {
            return getStatusDetailByTag(KNOWN);
        }

        private boolean getStatusDetailByTag(final String tagName) {
            return scenario.getTags().stream()
                    .anyMatch(tag -> tag.getName().equalsIgnoreCase(tagName))
                    || feature.getTags().stream()
                    .anyMatch(tag -> tag.getName().equalsIgnoreCase(tagName));
        }

        public boolean isResultTag(final PickleTag tag) {
            return Arrays.asList(new String[]{FLAKY, KNOWN, MUTED})
                    .contains(tag.getName().toUpperCase());
        }

        public boolean isPureSeverityTag(final PickleTag tag) {
            return Arrays.stream(SeverityLevel.values())
                    .map(SeverityLevel::value)
                    .map(value -> "@" + value)
                    .anyMatch(value -> value.equalsIgnoreCase(tag.getName()));
        }

    }

}
