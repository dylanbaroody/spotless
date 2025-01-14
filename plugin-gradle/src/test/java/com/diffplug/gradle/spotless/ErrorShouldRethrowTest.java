/*
 * Copyright 2016-2021 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.gradle.spotless;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import com.diffplug.common.base.CharMatcher;
import com.diffplug.common.base.Splitter;
import com.diffplug.common.base.StringPrinter;
import com.diffplug.spotless.LineEnding;

/** Tests the desired behavior from https://github.com/diffplug/spotless/issues/46. */
class ErrorShouldRethrowTest extends GradleIntegrationHarness {
	private void writeBuild(String... toInsert) throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("plugins {");
		lines.add("    id 'com.diffplug.spotless'");
		lines.add("    id 'java'");
		lines.add("}");
		lines.add("spotless {");
		lines.add("    format 'misc', {");
		lines.add("        lineEndings 'UNIX'");
		lines.add("        target file('README.md')");
		lines.add("        custom 'no swearing', {");
		lines.add("             if (it.toLowerCase(Locale.ROOT).contains('fubar')) {");
		lines.add("                 throw new RuntimeException('No swearing!');");
		lines.add("             }");
		lines.add("        }");
		lines.addAll(Arrays.asList(toInsert));
		setFile("build.gradle").toContent(String.join("\n", lines));
	}

	@Test
	void passesIfNoException() throws Exception {
		writeBuild(
				"    } // format",
				"}     // spotless");
		setFile("README.md").toContent("This code is fun.");
		runWithSuccess("> Task :spotlessMisc");
	}

	@Test
	void anyExceptionShouldFail() throws Exception {
		writeBuild(
				"    } // format",
				"}     // spotless");
		setFile("README.md").toContent("This code is fubar.");
		runWithFailure(
				"> Task :spotlessMisc FAILED\n" +
						"Step 'no swearing' found problem in 'README.md':\n" +
						"No swearing!\n" +
						"java.lang.RuntimeException: No swearing!\n");
	}

	@Test
	void unlessEnforceCheckIsFalse() throws Exception {
		writeBuild(
				"    } // format",
				"    enforceCheck false",
				"}     // spotless");
		setFile("README.md").toContent("This code is fubar.");
		runWithSuccess("> Task :compileJava NO-SOURCE");
	}

	@Test
	void unlessExemptedByStep() throws Exception {
		writeBuild(
				"        ignoreErrorForStep 'no swearing'",
				"    } // format",
				"}     // spotless");
		setFile("README.md").toContent("This code is fubar.");
		runWithSuccess("> Task :spotlessMisc\n" +
				"Unable to apply step 'no swearing' to 'README.md'");
	}

	@Test
	void unlessExemptedByPath() throws Exception {
		writeBuild(
				"        ignoreErrorForPath 'README.md'",
				"    } // format",
				"}     // spotless");
		setFile("README.md").toContent("This code is fubar.");
		runWithSuccess("> Task :spotlessMisc",
				"Unable to apply step 'no swearing' to 'README.md'");
	}

	@Test
	void failsIfNeitherStepNorFileExempted() throws Exception {
		writeBuild(
				"        ignoreErrorForStep 'nope'",
				"        ignoreErrorForPath 'nope'",
				"    } // format",
				"}     // spotless");
		setFile("README.md").toContent("This code is fubar.");
		runWithFailure("> Task :spotlessMisc FAILED\n" +
				"Step 'no swearing' found problem in 'README.md':\n" +
				"No swearing!\n" +
				"java.lang.RuntimeException: No swearing!\n");
	}

	private void runWithSuccess(String... messages) throws Exception {
		BuildResult result = gradleRunner().withArguments("check").build();
		assertResultAndMessages(result, TaskOutcome.SUCCESS, messages);
	}

	private void runWithFailure(String... messages) throws Exception {
		BuildResult result = gradleRunner().withArguments("check").buildAndFail();
		assertResultAndMessages(result, TaskOutcome.FAILED, messages);
	}

	private void assertResultAndMessages(BuildResult result, TaskOutcome outcome, String... messages) {
		String expectedToStartWith = StringPrinter.buildStringFromLines(messages).trim();
		int numNewlines = CharMatcher.is('\n').countIn(expectedToStartWith);
		List<String> actualLines = Splitter.on('\n').splitToList(LineEnding.toUnix(result.getOutput().trim()));
		String actualStart = String.join("\n", actualLines.subList(0, numNewlines + 1));
		Assertions.assertThat(actualStart).isEqualTo(expectedToStartWith);
		Assertions.assertThat(result.tasks(outcome).size() + result.tasks(TaskOutcome.UP_TO_DATE).size() + result.tasks(TaskOutcome.NO_SOURCE).size())
				.isEqualTo(result.getTasks().size());
	}
}
