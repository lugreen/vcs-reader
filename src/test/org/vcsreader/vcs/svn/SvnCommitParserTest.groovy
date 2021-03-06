package org.vcsreader.vcs.svn

import org.junit.Test
import org.vcsreader.vcs.Change
import org.vcsreader.vcs.Commit

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat
import static org.vcsreader.VcsChange.Type.*
import static org.vcsreader.VcsChange.noRevision
import static org.vcsreader.lang.DateTimeUtil.dateTime
import static org.vcsreader.vcs.TestUtil.assertEqualCommits

class SvnCommitParserTest {
	@Test void "parse commit with single change"() {
		def xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <log>
                <logentry revision="1">
                <author>Some Author</author>
                <date>2014-08-10T15:00:00.000000Z</date>
                <paths>
                        <path prop-mods="false" text-mods="true" kind="file" action="A">/file1.txt</path>
                </paths>
                <msg>initial commit</msg>
                </logentry>
            </log>
        """.trim()

		assertEqualCommits(SvnCommitParser.parseCommits(xml), [
				new Commit(
						"1", noRevision,
						dateTime("15:00:00 10/08/2014"),
						"Some Author",
						"initial commit",
						[new Change(Added, "file1.txt", "1")]
				)
		])
	}

	@Test void "ignore commits with kind equal to 'folder'"() {
		def xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <log>
                <logentry revision="1">
                <author>Some Author</author>
                <date>2013-12-21T19:35:02.544940Z</date>
                <paths>
                    <path prop-mods="false" text-mods="true" kind="folder" action="A">/folder</path>
                    <path kind="" action="A">/file.txt</path>
                </paths>
                <msg>commit message</msg>
                </logentry>
            </log>
        """.trim()

		assertEqualCommits(SvnCommitParser.parseCommits(xml), [
				new Commit(
						"1", noRevision,
						dateTime("19:35:02.544940 21/12/2013"),
						"Some Author",
						"commit message",
						[new Change(Added, "file.txt", "1")]
				)
		])
	}

	@Test void "parse commit with long message"() {
		def xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <log>
                <logentry revision="1696">
                <author>ebruneton</author>
                <date>2013-10-12T14:39:22.432589Z</date>
                <paths><path kind="" action="M">/some/file</path></paths>
<msg>- Update the product version to 5.0 beta
- Fix a few "bugs" found with Findbugs
- Switch to JDK 8 to run the tests.</msg>
</logentry>
            </log>
        """.trim()

		String comment = SvnCommitParser.parseCommits(xml)[0].message
		assert comment ==
				"- Update the product version to 5.0 beta\n" +
				"- Fix a few \"bugs\" found with Findbugs\n" +
				"- Switch to JDK 8 to run the tests."
	}

	@Test void "should skip deletion change when file is moved"() {
		def xml = """<?xml version="1.0" encoding="UTF-8"?>
			<log>
			<logentry revision="4">
				<author>Some Author</author>
				<date>2014-08-13T15:00:00.000000Z</date>
				<paths>
					<path kind="dir"
					   action="A"
					   prop-mods="false"
					   text-mods="false">/folder1</path>
					<path action="D"
					   prop-mods="false"
					   text-mods="false"
					   kind="file">/file1.txt</path>
					<path action="A"
					   prop-mods="false"
					   text-mods="false"
					   kind="file"
					   copyfrom-path="/file1.txt"
					   copyfrom-rev="1">/folder1/file1.txt</path>
				</paths>
				<msg>moved file1</msg>
			</logentry>
			</log>
        """.trim()

		assertThat(SvnCommitParser.parseCommits(xml)[0], equalTo(
				new Commit(
						"4", "3",
						dateTime("15:00:00 13/08/2014"),
						"Some Author",
						"moved file1",
						[new Change(Moved, "folder1/file1.txt", "file1.txt", "4", "1")]
				)
		))
	}

	@Test void "if 'text-mods' attribute is missing assume it's text change"() {
		def xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <log>
                <logentry revision="1">
                <author>Some Author</author>
                <date>2014-08-10T15:00:00.000000Z</date>
                <paths><path kind="" action="M">/some/file</path></paths>
                <msg>commit message</msg>
                </logentry>
            </log>
        """.trim()

		assertEqualCommits(SvnCommitParser.parseCommits(xml), [
				new Commit(
						"1", noRevision,
						dateTime("15:00:00 10/08/2014"),
						"Some Author",
						"commit message",
						[new Change(Modified, "some/file", "some/file", "1", noRevision)]
				)
		])
	}
}
