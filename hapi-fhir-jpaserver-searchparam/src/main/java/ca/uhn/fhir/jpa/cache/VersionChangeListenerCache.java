package ca.uhn.fhir.jpa.cache;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.searchparam.matcher.InMemoryMatchResult;
import ca.uhn.fhir.jpa.searchparam.matcher.SearchParamMatcher;
import ca.uhn.fhir.model.primitive.IdDt;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class VersionChangeListenerCache {
	private static final Logger ourLog = LoggerFactory.getLogger(VersionChangeListenerCache.class);

	@Autowired
	FhirContext myFhirContext;
	@Autowired
	SearchParamMatcher mySearchParamMatcher;

	private final SetValuedMap<String, VersionChangeListenerWithSearchParamMap> myListenersByResourceType = new HashSetValuedHashMap<>();

	public void add(String theResourceType, IVersionChangeListener theVersionChangeListener, SearchParameterMap theMap) {
		getListenerEntries(theResourceType).add(new VersionChangeListenerWithSearchParamMap(theVersionChangeListener, theMap));
	}

	@VisibleForTesting
	public void clearListenersForUnitTest() {
		myListenersByResourceType.clear();
	}

	public Set<String> resourceNames() {
		return myListenersByResourceType.keySet();
	}

	@Nonnull
	public Set<VersionChangeListenerWithSearchParamMap> getListenerEntries(String theResourceType) {
		return myListenersByResourceType.get(theResourceType);
	}

	public boolean hasListenerFor(IBaseResource theResource) {
		String resourceName = myFhirContext.getResourceType(theResource);
		return myListenersByResourceType.get(resourceName).stream().anyMatch(entry -> matches(entry.getSearchParameterMap(), theResource));
	}

	private boolean matches(SearchParameterMap theSearchParameterMap, IBaseResource theResource) {
		InMemoryMatchResult result = mySearchParamMatcher.match(theSearchParameterMap, theResource);
		if (!result.isInMemory()) {
			// FIXME KHS detect this at registration time
			ourLog.warn("Search Parameter Map {} cannot be processed in-memory", theSearchParameterMap);
		}
		return result.matched();
	}

	// FIXME KHS ensure we reset cache
	public VersionChangeResult notifyListener(VersionChangeListenerWithSearchParamMap theListenerEntry, ResourceVersionCache theOldResourceVersionCache, ResourceVersionMap theNewResourceVersionMap) {
		VersionChangeResult retval;
		IVersionChangeListener versionChangeListener = theListenerEntry.getVersionChangeListener();
		if (theListenerEntry.isInitialized()) {
			retval = compareLastVersionMapToNewVersionMapAndNotifyListenerOfChanges(versionChangeListener, theOldResourceVersionCache, theNewResourceVersionMap);
		} else {
			theOldResourceVersionCache.initialize(theNewResourceVersionMap);
			versionChangeListener.handleInit(theNewResourceVersionMap.getSourceIds());
			retval = VersionChangeResult.fromAdded(theNewResourceVersionMap.size());
			theListenerEntry.setInitialized(true);
		}
		return retval;
	}

	public VersionChangeResult compareLastVersionMapToNewVersionMapAndNotifyListenerOfChanges(IVersionChangeListener theListener, ResourceVersionCache theOldResourceVersionCache, ResourceVersionMap theNewResourceVersionMap) {
		Set<IdDt> newKeys = new HashSet<>();
		long added = 0;
		long updated = 0;
		long removed = 0;
		for (IdDt id : theNewResourceVersionMap.keySet()) {
			newKeys.add(id);
			String previousValue = theOldResourceVersionCache.addOrUpdate(id, theNewResourceVersionMap.get(id));
			IdDt newId = id.withVersion(theNewResourceVersionMap.get(id));
			if (previousValue == null) {
				theListener.handleCreate(newId);
				++added;
			} else if (!theNewResourceVersionMap.get(id).equals(previousValue)) {
				theListener.handleUpdate(newId);
				++updated;
			}
		}

		// If the NEW ResourceVersionMap does NOT have OLD key - delete it
		Set<IdDt> deletedIDs = new HashSet<>();
		for (String key : theOldResourceVersionCache.keySet()) {
			Map<IdDt, String> oldVersionCache = theOldResourceVersionCache.getMap(key);
			oldVersionCache.keySet()
				.forEach(k -> {
					if (!newKeys.contains(k)) {
						if (!deletedIDs.contains(k)) {
							theListener.handleDelete(k);
							deletedIDs.add(k);
						}
					}
				});
			removed += deletedIDs.size();
		}
		return VersionChangeResult.fromAddedUpdatedRemoved(added, updated, removed);
	}

	public void remove(IVersionChangeListener theVersionChangeListener) {
		myListenersByResourceType.entries().removeIf(entry -> entry.getValue().getVersionChangeListener().equals(theVersionChangeListener));
	}

	public int size() {
		return myListenersByResourceType.size();
	}
}
