package org.octopusden.octopus.jira.api.dto;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class IPSRequirementTest {

    private IPSRequirement requirement(List<String> labels, List<String> impact) {
        return new IPSRequirement("KEY-1", "name", "Open", labels, impact,
                "", "", "", Collections.emptyList(), Collections.emptyList());
    }

    @Test
    public void derivesAcquirerAndIssuerWhenLabelsPresent() {
        assertEquals(Arrays.asList("acquirer&issuer", "online&clearing"),
                requirement(Arrays.asList("IMPACT_ON_ACQ", "IMPACT_ON_ISS"), null).getImpact());
        assertEquals(Arrays.asList("acquirer", "online&clearing"),
                requirement(Collections.singletonList("IMPACT_ON_ACQ"), null).getImpact());
        assertEquals(Arrays.asList("issuer", "online&clearing"),
                requirement(Collections.singletonList("IMPACT_ON_ISS"), null).getImpact());

        assertEquals(Collections.singletonList("clearing"),
                requirement(Collections.singletonList("NO_IMPACT_ON_ONLINE"), null).getImpact());
        assertEquals(Collections.singletonList("online"),
                requirement(Collections.singletonList("NO_IMPACT_ON_CLEARING"), null).getImpact());
    }

    @Test
    public void derivesOnlineAndClearingWhenLabelsAbsent() {
        assertEquals(Collections.singletonList("online&clearing"),
                requirement(Collections.emptyList(), null).getImpact());
    }


    @Test
    public void derivesNoUpdatesWhenAllImpactLabelsAbsent() {
        assertEquals(Collections.singletonList("no updates for all"),
                requirement(Arrays.asList("NO_IMPACT_ON_ONLINE", "NO_IMPACT_ON_CLEARING"), null).getImpact());
    }

    @Test
    public void explicitImpactOverridesDerivation() {
        assertEquals(Collections.singletonList("custom"),
                requirement(Collections.emptyList(), Collections.singletonList("custom")).getImpact());
    }
}
