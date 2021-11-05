/*
 * Copyright 2021 AERIS-Consulting e.U.
 *
 * AERIS-Consulting e.U. licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.aerisconsulting.catadioptre.test;

import io.aerisconsulting.catadioptre.Testable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PublicType extends AbstractCatadioptreExample<Double, Optional<String>> {

	@Testable
	private final Map<String, Double> markers;

	public PublicType(final Map<String, Double> markers, final Double typedProperty,
			final Optional<String> typedProperty2) {
		super(typedProperty, typedProperty2);
		this.markers = markers;
	}

	@Testable
	private List<PackageType> callMethodWithLowVisibilityReturnType() {
		return Collections.singletonList(new PackageType());
	}

	@Testable
	private Double multiplySum(double multiplier, Double... valuesToSum) {
		return Arrays.stream(valuesToSum).filter(Objects::nonNull).mapToDouble(d -> d).sum() * multiplier;
	}
}
