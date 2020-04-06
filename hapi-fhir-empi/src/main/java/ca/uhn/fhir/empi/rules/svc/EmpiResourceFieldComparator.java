package ca.uhn.fhir.empi.rules.svc;

/*-
 * #%L
 * hapi-fhir-empi-rules
 * %%
 * Copyright (C) 2014 - 2020 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.empi.rules.json.EmpiFieldMatchJson;
import ca.uhn.fhir.empi.rules.json.IEmpiMatcher;
import ca.uhn.fhir.util.FhirTerser;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;

/**
 * This class is responsible for performing matching between raw-typed values of a left record and a right record.
 */
public class EmpiResourceFieldComparator implements IEmpiMatcher<IBaseResource> {
	private final FhirContext myFhirContext;
	private final EmpiFieldMatchJson myEmpiFieldMatchJson;
	private final String myResourceType;
	private final String myResourcePath;

	public EmpiResourceFieldComparator(FhirContext theFhirContext, EmpiFieldMatchJson theEmpiFieldMatchJson) {
		myFhirContext = theFhirContext;
		myEmpiFieldMatchJson = theEmpiFieldMatchJson;
		myResourceType = theEmpiFieldMatchJson.getResourceType();
		myResourcePath = theEmpiFieldMatchJson.getResourcePath();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean match(IBaseResource theLeftResource, IBaseResource theRightResource) {
		validate(theLeftResource);
		validate(theRightResource);

		FhirTerser terser = myFhirContext.newTerser();
		List<IBase> leftValues = terser.getValues(theLeftResource, myResourcePath, IBase.class);
		List<IBase> rightValues = terser.getValues(theRightResource, myResourcePath, IBase.class);
		return match(leftValues, rightValues);
	}

	@SuppressWarnings("rawtypes")
	private boolean match(List<IBase> theLeftValues, List<IBase> theRightValues) {
		boolean retval = false;
		for (IBase leftValue : theLeftValues) {
			for (IBase rightValue : theRightValues) {
				retval |= match(leftValue, rightValue);
			}
		}
		return retval;
	}

	private boolean match(IBase theLeftValue, IBase theRightValue) {
		return myEmpiFieldMatchJson.getMetric().similarity(myFhirContext, theLeftValue, theRightValue) >= myEmpiFieldMatchJson.getMatchThreshold();
	}

	private void validate(IBaseResource theResource) {
		String resourceType = theResource.getIdElement().getResourceType();
		Validate.notNull(resourceType, "Resource type may not be null");
		Validate.isTrue(myResourceType.equals(resourceType), "Expecting resource type %s got resource type %s", myResourceType, resourceType);
	}
}
