package com.offsec.nethunter;

final class KernelFlashCommand {
    private KernelFlashCommand() {
    }

    static String buildInstallCommand(String zipPath, String workDir,
                                      String primaryBusyBox, String fallbackBusyBox) {
        String zip = shellQuote(zipPath);
        String dir = shellQuote(workDir);
        String installer = shellQuote(workDir + "/update-binary");
        String copiedZip = shellQuote(workDir + "/anykernel.zip");
        String tempDir = shellQuote(workDir + "/tmp");

        return "BB=" + shellQuote(primaryBusyBox) + "; "
                + "[ -x \"$BB\" ] || BB=" + shellQuote(fallbackBusyBox) + "; "
                + "[ -x \"$BB\" ] || { echo 'BusyBox not found' >&2; exit 127; }; "
                + "mkdir -p " + dir + " && "
                + "\"$BB\" unzip -p -o " + zip
                + " 'META-INF/com/google/android/update-binary' > " + installer + " && "
                + "cp " + zip + " " + copiedZip + " && "
                + "\"$BB\" chmod 755 " + installer + " && "
                + "\"$BB\" chown root:root " + installer + " && "
                + "cd " + dir + " && "
                + "[ -s './update-binary' ] || { echo 'No installer script found' >&2; exit 1; }; "
                + "AKHOME=" + tempDir + " \"$BB\" ash './update-binary' 3 1 " + copiedZip;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
