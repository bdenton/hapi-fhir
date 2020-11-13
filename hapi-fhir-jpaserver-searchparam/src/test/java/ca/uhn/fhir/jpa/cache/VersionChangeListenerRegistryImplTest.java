package ca.uhn.fhir.jpa.cache;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.model.sched.ISchedulerService;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.parser.DataFormatException;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class VersionChangeListenerRegistryImplTest {
	private static FhirContext ourFhirContext = FhirContext.forR4();

	@Autowired
	VersionChangeListenerRegistryImpl myVersionChangeListenerRegistry;
	@MockBean
	private ISchedulerService mySchedulerService;
	@MockBean
	private IResourceVersionSvc myResourceVersionSvc;
	@MockBean
	private VersionChangeListenerCache myVersionChangeListenerCache;

	private IVersionChangeListener myTestListener = mock(IVersionChangeListener.class);
	private SearchParameterMap myMap = SearchParameterMap.newSynchronous();

	@Configuration
	static class SpringContext {
		@Bean
		public IVersionChangeListenerRegistry versionChangeListenerRegistry() {
			return new VersionChangeListenerRegistryImpl();
		}
		@Bean
		public FhirContext fhirContext() {
			return ourFhirContext;
		}
	}

	@BeforeEach
	public void before() {
		// FIXME KHS move to IT
//		myFemaleMap = new SearchParameterMap();
//		myFemaleMap.setLoadSynchronous(true);
//		myFemaleMap.add("gender", new TokenParam("female"));
		when(myResourceVersionSvc.getVersionMap("Patient", myMap)).thenReturn(ResourceVersionMap.fromResourceIds(new ArrayList<>()));
		Set<VersionChangeListenerWithSearchParamMap> entries = new HashSet<>();
		VersionChangeListenerWithSearchParamMap entry = new VersionChangeListenerWithSearchParamMap(myTestListener, myMap);
		entries.add(entry);
		when(myVersionChangeListenerCache.getListenerEntries("Patient")).thenReturn(entries);
		when(myVersionChangeListenerCache.notifyListener(any(), any(), any())).thenReturn(new VersionChangeResult());
	}

	@Test
	public void addingListenerForNonResourceFails() {
		try {
			myVersionChangeListenerRegistry.registerResourceVersionChangeListener("Foo", myMap, myTestListener);
			fail();
		} catch (DataFormatException e) {
			assertEquals("Unknown resource name \"Foo\" (this name is not known in FHIR version \"R4\")", e.getMessage());
		}
	}

	@Test
	public void addingListenerResetsTimer() {
		myVersionChangeListenerRegistry.registerResourceVersionChangeListener("Patient", myMap, myTestListener);
		myVersionChangeListenerRegistry.forceRefresh("Patient");
		assertNotEquals(Instant.MIN, myVersionChangeListenerRegistry.getNextRefreshTimeForUnitTest("Patient"));

		// Add a second listener to reset the timer
		myVersionChangeListenerRegistry.registerResourceVersionChangeListener("Patient", myMap, mock(IVersionChangeListener.class));
		assertEquals(Instant.MIN, myVersionChangeListenerRegistry.getNextRefreshTimeForUnitTest("Patient"));
	}

	@Test
	public void doNotRefreshIfNotMatches() {
		// FIXME KHS create IT version of this test that tests with actual searchparams
		myVersionChangeListenerRegistry.registerResourceVersionChangeListener("Patient", myMap, mock(IVersionChangeListener.class));
		myVersionChangeListenerRegistry.forceRefresh("Patient");
		assertNotEquals(Instant.MIN, myVersionChangeListenerRegistry.getNextRefreshTimeForUnitTest("Patient"));

		Patient patient = new Patient();

		// Don't reset timer if it doesn't match any searchparams
		when(myVersionChangeListenerCache.hasListenerFor(patient)).thenReturn(false);
		myVersionChangeListenerRegistry.requestRefreshIfWatching(patient);

		assertNotEquals(Instant.MIN, myVersionChangeListenerRegistry.getNextRefreshTimeForUnitTest("Patient"));

		// Reset timer if it does match searchparams
		when(myVersionChangeListenerCache.hasListenerFor(patient)).thenReturn(true);
		myVersionChangeListenerRegistry.requestRefreshIfWatching(patient);

		assertEquals(Instant.MIN, myVersionChangeListenerRegistry.getNextRefreshTimeForUnitTest("Patient"));
	}
}
