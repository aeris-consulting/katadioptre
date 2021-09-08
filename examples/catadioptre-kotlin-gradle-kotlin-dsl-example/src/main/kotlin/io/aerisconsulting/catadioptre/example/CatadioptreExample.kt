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
package io.aerisconsulting.catadioptre.example

import io.aerisconsulting.catadioptre.KTestable
import java.util.Optional

@Suppress("kotlin:S1144")
internal class CatadioptreExample : AbstractCatadioptreExample<Double, Optional<String>>() {

    @KTestable
    private var defaultProperty: Map<String, Double>? = mutableMapOf("any" to 1.0)

    @KTestable
    @Suppress("kotlin:S1144")
    private fun multiplySum(multiplier: Double = 1.0, vararg valuesToSum: Double?): Double {
        return valuesToSum.filterNotNull().sum() * multiplier
    }

    /**
     * This class can actually not be tested, because the type InternalCatadioptreExample is private.
     * But it should not generate any compilation issue and be simply ignored.
     */
    @KTestable
    private fun createListOfInternalClasses() = listOf(InternalCatadioptreExample())

    private class InternalCatadioptreExample
}
