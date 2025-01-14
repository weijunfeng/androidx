/*
 * Copyright 2023 The Android Open Source Project
 *
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
 */

package androidx.kruth

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MapSubjectTest {

    @Test
    fun isEmpty() {
        assertThat(mapOf<Any, Any>()).isEmpty()
    }

    @Test
    fun isEmptyWithFailure() {
        assertFailsWith<AssertionError> {
            assertThat(mapOf(1 to 5)).isEmpty()
        }
    }

    @Test
    fun containsKey() {
        assertThat(mapOf("kurt" to "kluever")).containsKey("kurt")
    }

    @Test
    fun containsKeyFailure() {
        val actual = mapOf("kurt" to "kluever")
        assertFailsWith<AssertionError> {
            assertThat(actual).containsKey("greg")
        }
    }

    @Test
    fun containsKeyNullFailure() {
        assertFailsWith<AssertionError> {
            assertThat(mapOf("kurt" to "kluever")).containsKey(null)
        }
    }

    @Test
    fun containsKey_failsWithSameToString() {
        assertFailsWith<AssertionError> {
            assertThat(mapOf(1L to "value1", 2L to "value2", "1" to "value3")).containsKey(1)
        }
    }

    @Test
    fun containsKey_failsWithNullStringAndNull() {
        assertFailsWith<AssertionError> {
            assertThat(mapOf("null" to "value1")).containsKey(null)
        }
    }

    @Test
    fun containsNullKey() {
        assertThat(mapOf(null to "null")).containsKey(null)
    }

    @Test
    fun failMapContainsKey() {
        assertFailsWith<AssertionError> {
            assertThat(mapOf("a" to "A")).containsKey("b")
        }
    }

    @Test
    fun failMapContainsKeyWithNull() {
        assertFailsWith<AssertionError> {
            assertThat(mapOf("a" to "A")).containsKey(null)
        }
    }
}
