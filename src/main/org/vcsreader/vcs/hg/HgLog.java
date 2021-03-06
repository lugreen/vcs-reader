package org.vcsreader.vcs.hg;

import org.vcsreader.LogResult;
import org.vcsreader.VcsCommit;
import org.vcsreader.lang.CommandLine;
import org.vcsreader.lang.TimeRange;
import org.vcsreader.vcs.VcsCommand;
import org.vcsreader.vcs.VcsError;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.vcsreader.vcs.hg.HgUtil.containsHgRepo;
import static org.vcsreader.vcs.hg.HgUtil.isSuccessful;

// suppress because it's similar to GitLog
@SuppressWarnings("Duplicates")
class HgLog implements VcsCommand<LogResult> {
	private final String hgPath;
	private final String repoFolder;
	private final TimeRange timeRange;
	private final CommandLine commandLine;


	public HgLog(String hgPath, String repoFolder, TimeRange timeRange) {
		this.hgPath = hgPath;
		this.repoFolder = repoFolder;
		this.timeRange = timeRange;
		this.commandLine = hgLog(hgPath, repoFolder, timeRange);
	}

	@Override public LogResult execute() {
		if (!containsHgRepo(repoFolder)) {
			throw new VcsError("Folder doesn't contain git repository: '" + repoFolder + "'.");
		}

		commandLine.execute();

		if (isSuccessful(commandLine)) {
			List<VcsCommit> commits = HgCommitParser.parseListOfCommits(commandLine.stdout());
			List<Exception> errors = (commandLine.stderr().trim().isEmpty() ? new ArrayList<>() : asList(new VcsError(commandLine.stderr())));
			return new LogResult(commits, errors);
		} else {
			return new LogResult(new VcsError(commandLine.stderr()));
		}
	}

	@Override public String describe() {
		return commandLine.describe();
	}

	@Override public boolean cancel() {
		return commandLine.kill();
	}

	static CommandLine hgLog(String hgPath, String repoFolder, TimeRange timeRange) {
		CommandLine commandLine = new CommandLine(
				hgPath, "log",
				"--encoding", UTF_8.name(),
				"-r", "date(\"" + asHgInstant(timeRange.from()) + " to " + asHgInstant(timeRange.to()) + "\")",
				"--template", HgCommitParser.logTemplate()
		);
		return commandLine.workingDir(repoFolder).outputCharset(UTF_8);
	}

	private static String asHgInstant(Instant instant) {
		// see 'hg help dates'
		long epochSeconds = instant.getEpochSecond() - 1;
		long clampedEpochSeconds = min(Integer.MAX_VALUE, max(0, epochSeconds));
		String secondsSinceEpoch = Long.toString(clampedEpochSeconds);
		String utcOffset = "0";
		return secondsSinceEpoch + " " + utcOffset;
	}

	@SuppressWarnings("SimplifiableIfStatement")
	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		HgLog hgLog = (HgLog) o;

		if (hgPath != null ? !hgPath.equals(hgLog.hgPath) : hgLog.hgPath != null) return false;
		if (repoFolder != null ? !repoFolder.equals(hgLog.repoFolder) : hgLog.repoFolder != null) return false;
		if (timeRange != null ? !timeRange.equals(hgLog.timeRange) : hgLog.timeRange != null) return false;
		return commandLine != null ? commandLine.equals(hgLog.commandLine) : hgLog.commandLine == null;
	}

	@Override public int hashCode() {
		int result = hgPath != null ? hgPath.hashCode() : 0;
		result = 31 * result + (repoFolder != null ? repoFolder.hashCode() : 0);
		result = 31 * result + (timeRange != null ? timeRange.hashCode() : 0);
		result = 31 * result + (commandLine != null ? commandLine.hashCode() : 0);
		return result;
	}

	@Override public String toString() {
		return "HgLog{" +
				"hgPath='" + hgPath + '\'' +
				", repoFolder='" + repoFolder + '\'' +
				", timeRange=" + timeRange +
				", commandLine=" + commandLine +
				'}';
	}
}
