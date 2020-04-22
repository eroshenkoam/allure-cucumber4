package io.cucumber.junit;

import cucumber.runtime.model.CucumberFeature;
import gherkin.pickles.Pickle;
import gherkin.pickles.PickleTag;
import io.qameta.allure.ee.filter.FileTestPlanProvider;
import io.qameta.allure.ee.filter.TestPlan;
import io.qameta.allure.ee.filter.TestPlanTest;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AllureCucumber extends Cucumber {

    private final static String CUCUMBER_FEATURE_FIELD = "cucumberFeature";

    public AllureCucumber(final Class clazz) throws InitializationError {
        super(clazz);
    }

    protected void runChild(final FeatureRunner child, final RunNotifier notifier) {
        try {
            final Optional<Filter> filter = getFilter(child);
            if (filter.isPresent()) {
                child.filter(filter.get());
            }
            child.run(notifier);
        } catch (NoTestsRemainException e) {
            //do nothing
        }
    }

    private Optional<Filter> getFilter(final FeatureRunner child) {
        final Optional<TestPlan> testPlan = new FileTestPlanProvider().get();
        try {
            final Object field = FieldUtils.readDeclaredField(child, CUCUMBER_FEATURE_FIELD, true);
            final Optional<CucumberFeature> feature = Optional.ofNullable(field)
                    .filter(CucumberFeature.class::isInstance)
                    .map(CucumberFeature.class::cast);
            if (feature.isPresent() && testPlan.isPresent()) {
                return Optional.of(new AllureFilter(feature.get(), testPlan.get()));
            }
        } catch (IllegalAccessException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static class AllureFilter extends Filter {

        private static final String ID_NAME = "id";
        private static final Pattern ID_TAG = Pattern.compile("^@?allure\\.id[:=](?<id>.+)$");

        private final TestPlan testPlan;
        private final CucumberFeature feature;

        public AllureFilter(final CucumberFeature feature, final TestPlan testPlan) {
            this.feature = feature;
            this.testPlan = testPlan;
        }

        @Override
        public boolean shouldRun(final Description description) {
            final Optional<Pickle> pickle = getPickle(description);
            if (pickle.isPresent()) {
                final Optional<String> allureId = pickle.get().getTags().stream()
                        .map(PickleTag::getName)
                        .map(ID_TAG::matcher)
                        .filter(Matcher::matches)
                        .map(matcher -> matcher.group(ID_NAME))
                        .findFirst();
                final String selector = getSelector(description);
                if (allureId.isPresent()) {
                    final String id = allureId.get();
                    return testPlan.getTests().stream()
                            .map(TestPlanTest::getId)
                            .anyMatch(id::equals);
                } else {
                    return testPlan.getTests().stream()
                            .map(TestPlanTest::getSelector)
                            .anyMatch(selector::equals);
                }
            }
            return false;
        }

        @Override
        public String describe() {
            return "Allure test plan filter";
        }

        private String getSelector(final Description description) {
            return String.format("%s: %s", description.getClassName(), description.getMethodName());
        }

        private Optional<Pickle> getPickle(final Description description) {
            return feature.getPickles().stream()
                    .map(p -> p.pickle)
                    .filter(pickle -> pickle.getName().equals(description.getMethodName()))
                    .findFirst();
        }
    }

}
