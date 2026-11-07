import com.android.tools.build.apkzlib.zip.AlignmentRules;
import com.android.tools.build.apkzlib.zip.ZFileOptions;

/** Configure the pinned patcher's existing alignment policy BEFORE it writes/signs.
 * No ZIP rebuild after patching: nested entries must keep shared byte ranges.
 */
public final class CompactPatch {
    public static void main(String[] args) throws Exception {
        var field = org.lsposed.patch.ApkPatcher.class.getDeclaredField("Z_FILE_OPTIONS");
        field.setAccessible(true);
        var options = (ZFileOptions) field.get(null);
        options.setAlignmentRule(AlignmentRules.compose(
            AlignmentRules.constantForSuffix(".so", 16384),
            AlignmentRules.constantForSuffix("assets/lspatch/origin.apk", 16384),
            AlignmentRules.constant(4)
        ));
        org.lsposed.patch.LSPatch.main(args);
    }
}
