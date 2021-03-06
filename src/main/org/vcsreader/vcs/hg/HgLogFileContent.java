package org.vcsreader.vcs.hg;

import org.vcsreader.LogFileContentResult;
import org.vcsreader.lang.CommandLine;
import org.vcsreader.vcs.VcsCommand;

import java.nio.charset.Charset;

import static org.vcsreader.lang.StringUtil.trimLastNewLine;
import static org.vcsreader.vcs.hg.HgUtil.isSuccessful;


/**
 * See https://selenic.com/hg/help/cat
 */
@SuppressWarnings("Duplicates") // Because it's similar to GitLogFileContent.
class HgLogFileContent implements VcsCommand<LogFileContentResult> {
	private final String pathToHg;
	private final String repoFolder;
	private final String filePath;
	private final String revision;
	private final Charset charset;
	private final CommandLine commandLine;

	public HgLogFileContent(String pathToHg, String repoFolder, String filePath, String revision, Charset charset) {
		this.pathToHg = pathToHg;
		this.repoFolder = repoFolder;
		this.filePath = filePath;
		this.revision = revision;
		this.charset = charset;
		this.commandLine = hgLogFileContent(pathToHg, repoFolder, filePath, revision, charset);
	}

	@Override public LogFileContentResult execute() {
		commandLine.execute();
		if (isSuccessful(commandLine)) {
			return new LogFileContentResult(trimLastNewLine(commandLine.stdout()));
		} else {
			return new LogFileContentResult(commandLine.stderr(), commandLine.exitCode());
		}
	}

	@Override public String describe() {
		return commandLine.describe();
	}

	@Override public boolean cancel() {
		return commandLine.kill();
	}

	static CommandLine hgLogFileContent(String pathToHg, String repoFolder, String filePath, String revision, Charset charset) {
		CommandLine commandLine = new CommandLine(pathToHg, "cat", "-r " + revision, filePath);
		return commandLine.workingDir(repoFolder).outputCharset(charset).charsetAutoDetect(true);
	}

	@SuppressWarnings("SimplifiableIfStatement")
	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		HgLogFileContent that = (HgLogFileContent) o;

		if (pathToHg != null ? !pathToHg.equals(that.pathToHg) : that.pathToHg != null) return false;
		if (repoFolder != null ? !repoFolder.equals(that.repoFolder) : that.repoFolder != null) return false;
		if (filePath != null ? !filePath.equals(that.filePath) : that.filePath != null) return false;
		if (revision != null ? !revision.equals(that.revision) : that.revision != null) return false;
		return !(charset != null ? !charset.equals(that.charset) : that.charset != null);

	}

	@Override public int hashCode() {
		int result = pathToHg != null ? pathToHg.hashCode() : 0;
		result = 31 * result + (repoFolder != null ? repoFolder.hashCode() : 0);
		result = 31 * result + (filePath != null ? filePath.hashCode() : 0);
		result = 31 * result + (revision != null ? revision.hashCode() : 0);
		result = 31 * result + (charset != null ? charset.hashCode() : 0);
		return result;
	}

	@Override public String toString() {
		return "HgLogFileContent{" +
				"pathToHg='" + pathToHg + '\'' +
				", repoFolder='" + repoFolder + '\'' +
				", filePath='" + filePath + '\'' +
				", revision='" + revision + '\'' +
				", charset=" + charset +
				'}';
	}
}
