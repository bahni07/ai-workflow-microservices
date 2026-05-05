package com.aiworkflow.eureka;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test: registration round trip.
 *
 * Feature: api-gateway, Property 3: Registration round trip
 * Validates: Requirements 1.4, 4.1, 4.6
 *
 * For any service name and port, registering an instance with the Eureka registry
 * and then querying the registry SHALL return an entry with matching appName,
 * instanceId, and port.
 *
 * Uses PeerAwareInstanceRegistry directly (no HTTP) for fast, deterministic testing.
 * Self-preservation is disabled to prevent interference with test registrations.
 * @JqwikSpringSupport enables @Autowired injection as method parameters in @Property methods.
 */
@JqwikSpringSupport
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "eureka.server.enable-self-preservation=false"
})
class RegistrationRoundTripPropertyTest {

    /** Reset registry state before each try to prevent cross-try contamination. */
    @BeforeTry
    void cleanRegistry(@Autowired PeerAwareInstanceRegistry registry) {
        registry.clearRegistry();
    }

    /**
     * Feature: api-gateway, Property 3: Registration round trip
     * Validates: Requirements 1.4, 4.1, 4.6
     *
     * For any lowercase service name and valid port, registering an instance and
     * querying the registry returns an entry with matching appName, instanceId, and port.
     */
    @Property(tries = 100)
    void registrationRoundTripPreservesAppNameInstanceIdAndPort(
            @ForAll("serviceNames") String appName,
            @ForAll @IntRange(min = 1024, max = 65535) int port,
            @Autowired PeerAwareInstanceRegistry registry) {

        String instanceId = appName + ":" + port;

        // Build a minimal InstanceInfo matching the Eureka registration contract
        InstanceInfo instance = InstanceInfo.Builder.newBuilder()
                .setAppName(appName)
                .setInstanceId(instanceId)
                .setHostName("localhost")
                .setIPAddr("127.0.0.1")
                .setPort(port)
                .setStatus(InstanceInfo.InstanceStatus.UP)
                .setLeaseInfo(LeaseInfo.Builder.newBuilder()
                        .setRenewalIntervalInSecs(30)
                        .setDurationInSecs(90)
                        .build())
                .build();

        // Register the instance
        registry.register(instance, false);

        // Query the registry and assert the round trip preserves all metadata
        Applications apps = registry.getApplications();
        Application app = apps.getRegisteredApplications(appName);

        assertThat(app).as("Application '%s' should be registered", appName).isNotNull();

        InstanceInfo retrieved = app.getByInstanceId(instanceId);
        assertThat(retrieved).as("Instance '%s' should be in registry", instanceId).isNotNull();
        assertThat(retrieved.getAppName()).isEqualToIgnoringCase(appName);
        assertThat(retrieved.getInstanceId()).isEqualTo(instanceId);
        assertThat(retrieved.getPort()).isEqualTo(port);
    }

    @Provide
    Arbitrary<String> serviceNames() {
        // Generate lowercase service names matching the naming convention
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(15);
    }
}
