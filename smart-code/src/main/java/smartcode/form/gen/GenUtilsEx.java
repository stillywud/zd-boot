//
// Source code recreated from GenUtilsEx .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package smartcode.form.gen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenUtilsEx {
    private static final Logger a = LoggerFactory.getLogger(GenUtilsEx.class);
    private String b;
    private List<File> c = new ArrayList();
    private String d;

    public GenUtilsEx(String var1) {
        a.debug("----templatePath-----------------" + var1);
        a.debug("----stylePath-----------------" + this.d);
        this.b = var1;
    }

    private void a(File var1) {
        this.aa(var1);
    }

    private void aa(File... var1) {
        this.c = Arrays.asList(var1);
    }

    public String a() {
        return this.d;
    }

    public void a(String var1) {
        this.d = var1;
    }

    public List<File> b() {
        String var1 = this.getClass().getResource(this.b).getFile();
        var1 = var1.replaceAll("%20", " ");
        a.debug("-------classpath-------" + var1);
        if (var1.indexOf("/BOOT-INF/classes!") != -1) {
            var1 = System.getProperty("user.dir") + File.separator + "config/jeecg/code-template-online/".replace("/", File.separator);
            a.debug("---JAR--config--classpath-------" + var1);
        }

        this.a(new File(var1));
        return this.c;
    }

    public void a(List<File> var1) {
        this.c = var1;
    }

    public String toString() {
        StringBuilder var1 = new StringBuilder();
        var1.append("{\"templateRootDirs\":\"");
        var1.append(this.c);
        var1.append("\",\"stylePath\":\"");
        var1.append(this.d);
        var1.append("\"} ");
        return var1.toString();
    }
}
