package ca.uhn.fhir.jpa.empi.provider;

import ca.uhn.fhir.jpa.empi.svc.EmpiCandidateSearchSvc;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;

@Lazy
@Service
public class EmpiProviderR4 {
	@Autowired
	EmpiCandidateSearchSvc myEmpiCandidateSearchSvc;

	@Operation(name="$match", type = Patient.class)
	public Bundle match(@OperationParam(name="resource", min = 1, max = 1) Patient thePatient) {
		Collection<IBaseResource> matches = myEmpiCandidateSearchSvc.findCandidates("Patient", thePatient);

		Bundle retVal = new Bundle();
		retVal.setType(Bundle.BundleType.SEARCHSET);
		retVal.setId(UUID.randomUUID().toString());
		retVal.getMeta().setLastUpdatedElement(InstantType.now());

		for (IBaseResource next : matches) {
			retVal.addEntry().setResource((Resource) next);
		}

		return retVal;
	}
}
