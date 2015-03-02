package gitbucket.core.util;

import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * This class helps to apply patch. Most of these code came from {@link org.eclipse.jgit.api.ApplyCommand}.
 */
public class PatchUtil {

    public static String apply(String source, String patch, FileHeader fh)
            throws IOException, PatchApplyException {
        RawText rt = new RawText(source.getBytes("UTF-8"));
        List<String> oldLines = new ArrayList<String>(rt.size());
        for (int i = 0; i < rt.size(); i++)
            oldLines.add(rt.getString(i));
        List<String> newLines = new ArrayList<String>(oldLines);
        for (HunkHeader hh : fh.getHunks()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(patch.getBytes("UTF-8"), hh.getStartOffset(), hh.getEndOffset() - hh.getStartOffset());
            RawText hrt = new RawText(out.toByteArray());
            List<String> hunkLines = new ArrayList<String>(hrt.size());
            for (int i = 0; i < hrt.size(); i++)
                hunkLines.add(hrt.getString(i));
            int pos = 0;
            for (int j = 1; j < hunkLines.size(); j++) {
                String hunkLine = hunkLines.get(j);
                switch (hunkLine.charAt(0)) {
                    case ' ':
                        if (!newLines.get(hh.getNewStartLine() - 1 + pos).equals(
                                hunkLine.substring(1))) {
                            throw new PatchApplyException(MessageFormat.format(
                                    JGitText.get().patchApplyException, hh));
                        }
                        pos++;
                        break;
                    case '-':
                        if (!newLines.get(hh.getNewStartLine() - 1 + pos).equals(
                                hunkLine.substring(1))) {
                            throw new PatchApplyException(MessageFormat.format(
                                    JGitText.get().patchApplyException, hh));
                        }
                        newLines.remove(hh.getNewStartLine() - 1 + pos);
                        break;
                    case '+':
                        newLines.add(hh.getNewStartLine() - 1 + pos,
                                hunkLine.substring(1));
                        pos++;
                        break;
                }
            }
        }
        if (!isNoNewlineAtEndOfFile(fh))
            newLines.add(""); //$NON-NLS-1$
        if (!rt.isMissingNewlineAtEnd())
            oldLines.add(""); //$NON-NLS-1$
        if (!isChanged(oldLines, newLines))
            return null; // don't touch the file
        StringBuilder sb = new StringBuilder();
        for (String l : newLines) {
            // don't bother handling line endings - if it was windows, the \r is
            // still there!
            sb.append(l).append('\n');
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private static boolean isChanged(List<String> ol, List<String> nl) {
        if (ol.size() != nl.size())
            return true;
        for (int i = 0; i < ol.size(); i++)
            if (!ol.get(i).equals(nl.get(i)))
                return true;
        return false;
    }

    private static boolean isNoNewlineAtEndOfFile(FileHeader fh) {
        HunkHeader lastHunk = fh.getHunks().get(fh.getHunks().size() - 1);
        RawText lhrt = new RawText(lastHunk.getBuffer());
        return lhrt.getString(lhrt.size() - 1).equals(
                "\\ No newline at end of file"); //$NON-NLS-1$
    }
}
