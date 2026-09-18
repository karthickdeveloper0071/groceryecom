package com.groceryecom;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * The architecture, as tests.
 *
 * <p>Every rule here is written down in {@code docs/} as well, and a rule that lives only
 * in a document is a rule somebody breaks on a Friday afternoon without meaning to. These
 * fail the build instead, at the moment the mistake is made, with a message naming the
 * class.
 *
 * <p>They are deliberately about <b>structure</b> rather than style: which package may
 * call which, what must not appear in a response, what must not reach a log. A formatter
 * handles style; these handle the decisions that are expensive to reverse once a hundred
 * classes have copied them.
 *
 * <p>Most of them have nothing to check yet, because no business module exists. That is
 * the point of writing them now: the first module is held to the rules from its first
 * class, rather than being asked to move afterwards.
 *
 * <p>When one of these fails, the dependency is usually wrong, not the rule. If a rule is
 * genuinely wrong, change it here <b>and</b> in the document that states it, in the same
 * commit - two sources of truth that disagree are worse than one that is out of date.
 */
class ArchitectureRulesTest {

    private static final String BASE = "com.groceryecom";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    // --- the three layers ------------------------------------------------------------

    /**
     * {@code shared} is the bottom of the stack: value types and exceptions that anything
     * may use. It depends on nothing of ours, so it can never be the reason two parts of
     * the system are tangled.
     */
    @Test
    void sharedDependsOnNothingOfOurs() {
        noClasses()
                .that().resideInAPackage(BASE + ".shared..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(BASE + ".platform..", BASE + ".modules..")
                .because("shared is the foundation: it is used by everything and uses none of it")
                .check(classes);
    }

    /**
     * {@code platform} is infrastructure every module gets for free - security, error
     * handling, logging. The moment it knows about a business module, it stops being
     * infrastructure and becomes part of that feature.
     */
    @Test
    void platformDoesNotDependOnBusinessModules() {
        noClasses()
                .that().resideInAPackage(BASE + ".platform..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".modules..")
                .because("platform serves every module and must not know about any of them")
                .check(classes);
    }

    // --- inside a module -------------------------------------------------------------

    /**
     * A controller reads the request, calls one service, returns the result. A controller
     * that reaches a repository directly is a controller with business logic in it, and
     * that logic cannot then be reused, tested without HTTP, or found by somebody looking
     * in the obvious place.
     */
    @Test
    void controllersDoNotTouchThePersistenceLayer() {
        noClasses()
                .that().resideInAPackage(BASE + ".modules..api..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".modules..domain..")
                .because("api calls application, application uses domain; a controller that "
                        + "loads entities is business logic in the wrong place")
                .allowEmptyShould(true)
                .check(classes);
    }

    /**
     * The domain is the innermost layer: entities and repositories that know about the
     * business and nothing about HTTP. A domain class importing a DTO or a service is the
     * dependency arrow pointing the wrong way.
     */
    @Test
    void theDomainKnowsNothingAboutTheLayersAboveIt() {
        noClasses()
                .that().resideInAPackage(BASE + ".modules..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(BASE + ".modules..api..", BASE + ".modules..application..")
                .because("domain is the innermost layer; api and application depend on it, never the reverse")
                .allowEmptyShould(true)
                .check(classes);
    }

    /**
     * {@code contract} is what other modules import, so it must stay a leaf: records,
     * enums and interfaces. A contract type that drags an entity along makes the entity
     * part of the public surface, and then the table cannot change without breaking
     * another module.
     */
    @Test
    void contractDoesNotLeakTheInsideOfItsModule() {
        noClasses()
                .that().resideInAPackage(BASE + ".modules..contract..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(BASE + ".modules..domain..", BASE + ".modules..application..",
                        BASE + ".modules..api..")
                .because("contract is the public surface: records, enums and interfaces only")
                .allowEmptyShould(true)
                .check(classes);
    }

    // --- what reaches the outside world ----------------------------------------------

    /**
     * An entity in a response publishes the database: every column, every lazy
     * association, every field somebody adds later. The mapper exists so a response
     * changes when we decide it should, not when a column does.
     */
    @Test
    void noEntityIsReturnedFromAController() {
        noMethods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should().haveRawReturnType(annotatedWithEntity())
                .because("responses are DTOs; an entity in a response exposes the schema and "
                        + "serialises whatever a lazy association touches")
                .allowEmptyShould(true)
                .check(classes);
    }

    /** The same applies in reverse: an entity as a request body lets a client set any column. */
    @Test
    void noEntityIsAcceptedAsARequestBody() {
        noMethods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should().haveRawParameterTypes(DescribedPredicate.anyElementThat(annotatedWithEntity()))
                .because("a request body is a DTO with validation; an entity would let a caller "
                        + "set fields the API never meant to expose")
                .allowEmptyShould(true)
                .check(classes);
    }

    // --- money and time ---------------------------------------------------------------

    /**
     * 0.1 + 0.2 is not 0.3 in binary floating point. Money is an integer count of the
     * smallest unit, which is what {@code Money} holds.
     */
    @Test
    void moneyIsNeverAFloatingPointNumber() {
        noFields()
                .that().haveNameMatching(".*(?i)(price|amount|total|cost|fee|balance|commission).*")
                .should().haveRawType(double.class)
                .orShould().haveRawType(float.class)
                .orShould().haveRawType(Double.class)
                .orShould().haveRawType(Float.class)
                .because("money is an integer count of minor units (shared.money.Money); "
                        + "floating point silently loses fractions of a sen")
                .check(classes);
    }

    /**
     * {@code Instant} is a point in time that means the same thing everywhere.
     * {@code Date} carries a hidden default time zone, which is how a report comes out a
     * day wrong for exactly one customer.
     */
    @Test
    void timeIsInstantNotTheLegacyDateClasses() {
        noClasses()
                .that().resideInAPackage(BASE + "..")
                // The one exception, and it is a library boundary rather than a choice:
                // the JWT library's builder takes java.util.Date. It is converted on the
                // way in and on the way out, so every method signature on that class is
                // still Instant and no Date escapes it. An exception that is named and
                // explained is a rule; an exception that is quietly allowed is not.
                .and().doNotHaveFullyQualifiedName(BASE + ".platform.security.JwtTokenProvider")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("java.util.Date")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.Calendar")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.sql.Timestamp")
                .because("timestamps are Instant, stored as TIMESTAMPTZ and always UTC; "
                        + "java.util.Date carries a hidden default time zone, which is how a "
                        + "report comes out a day wrong for exactly one customer")
                .check(classes);
    }

    // --- transactions and injection ---------------------------------------------------

    /**
     * A transaction belongs to a use case, not to a request. On a controller it also does
     * nothing useful: the proxy wraps the HTTP method, so the transaction is open while
     * the response is being serialised.
     */
    @Test
    void transactionsAreOnServicesNotControllers() {
        noMethods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should().beAnnotatedWith(Transactional.class)
                .because("@Transactional marks a use case in application, not an HTTP handler")
                .allowEmptyShould(true)
                .check(classes);

        noClasses()
                .that().areAnnotatedWith(RestController.class)
                .should().beAnnotatedWith(Transactional.class)
                .because("@Transactional marks a use case in application, not an HTTP handler")
                .allowEmptyShould(true)
                .check(classes);
    }

    /**
     * Constructor injection, always. A field-injected dependency cannot be set in a plain
     * unit test, hides how many collaborators a class has, and lets a class be
     * constructed in a half-built state.
     */
    @Test
    void dependenciesArriveThroughTheConstructor() {
        noFields()
                .should().beAnnotatedWith(Autowired.class)
                .because("constructor injection: it is testable without Spring and makes an "
                        + "over-large dependency list impossible to ignore")
                .check(classes);
    }

    // --- logging ----------------------------------------------------------------------

    /**
     * {@code System.out} bypasses the log configuration: no level, no trace id, no JSON in
     * production, and nothing anybody can search during an incident.
     */
    @Test
    void nothingPrintsToTheConsoleDirectly() {
        noClasses()
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .because("logging goes through SLF4J, which carries the level and the trace id")
                .check(classes);
    }

    // --- naming that keeps the structure findable --------------------------------------

    /**
     * Not decoration: the layer rules above are written in terms of packages, and a
     * service sitting in {@code domain} or a controller in {@code application} escapes
     * them silently.
     */
    @Test
    void classesLiveInThePackageTheirNameImplies() {
        ArchRule controllers = classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage(BASE + ".modules..api..")
                .because("a controller outside api escapes every rule written about api")
                .allowEmptyShould(true);

        ArchRule entities = classes()
                .that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage(BASE + ".modules..domain..")
                .because("an entity outside domain escapes the rules that keep it out of responses")
                .allowEmptyShould(true);

        controllers.check(classes);
        entities.check(classes);
    }

    /**
     * Every entity extends {@code BaseEntity}, which carries the id, the timestamps, the
     * soft-delete flags and the {@code @Version} column that makes a lost update
     * impossible. An entity that skips it has none of them, and nobody notices until two
     * people save the same row.
     */
    @Test
    void everyEntityExtendsBaseEntity() {
        classes()
                .that().areAnnotatedWith(Entity.class)
                .should().beAssignableTo(com.groceryecom.shared.persistence.BaseEntity.class)
                .because("BaseEntity carries the identity, timestamps and the @Version column "
                        + "that stops two writers overwriting each other")
                .allowEmptyShould(true)
                .check(classes);
    }

    /** Request and response types are records: immutable, with no behaviour to hide. */
    @Test
    void apiDtosAreRecords() {
        classes()
                .that().resideInAPackage(BASE + ".modules..api.dto..")
                .should().beRecords()
                .because("a DTO is data; a record cannot grow a setter or a method by accident")
                .allowEmptyShould(true)
                .check(classes);
    }

    /**
     * A public field on any class is a field anything can change. On an entity it also
     * defeats Hibernate's dirty checking and lazy loading.
     */
    @Test
    void noPublicMutableFields() {
        fields()
                .that().arePublic()
                .and().areNotStatic()
                .should().beFinal()
                .because("a mutable public field can be changed by anything, from anywhere")
                .allowEmptyShould(true)
                .check(classes);
    }

    private static DescribedPredicate<JavaClass> annotatedWithEntity() {
        return new DescribedPredicate<>("annotated with @Entity") {
            @Override
            public boolean test(JavaClass javaClass) {
                return javaClass.isAnnotatedWith(Entity.class);
            }
        };
    }
}
