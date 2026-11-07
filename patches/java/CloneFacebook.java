import com.reandroid.apk.ApkModule;
import com.reandroid.arsc.chunk.xml.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.instruction.formats.*;
import org.jf.dexlib2.iface.reference.*;
import org.jf.dexlib2.iface.value.*;
import org.jf.dexlib2.immutable.instruction.*;
import org.jf.dexlib2.immutable.reference.*;
import org.jf.dexlib2.immutable.value.*;
import org.jf.dexlib2.rewriter.*;

/** Surgical identity rewrite. Class/type/method names are deliberately preserved. */
public class CloneFacebook {
    static final String OLD = "com.facebook.katana", NEW = "com.tas33n.facebook.extended";
    static final Map<String,String> map = new TreeMap<>();
    static PrintWriter log;
    static String mapped(String s) {
        if (s == null) return null;
        if (map.containsKey(s)) return map.get(s);
        for (var e:map.entrySet()) {
            String uri = "content://"+e.getKey();
            if (s.equals(uri) || s.startsWith(uri+"/") || s.startsWith(uri+"?"))
                return "content://"+e.getValue()+s.substring(uri.length());
        }
        if (s.startsWith(OLD+":")) return NEW+s.substring(OLD.length());
        return s;
    }
    static void add(String s) {
        if (s != null) map.put(s, s.startsWith(OLD) ? NEW+s.substring(OLD.length()) : s+".tas33n.extended");
    }
    static List<ResXmlElement> elements(ResXmlElement root) {
        List<ResXmlElement> out=new ArrayList<>(); out.add(root);
        Iterator<?> it=root.recursiveElements();
        while(it.hasNext()) { var e=(ResXmlElement)it.next(); if(e!=root)out.add(e); }
        return out;
    }
    static String value(ResXmlElement e,String name) {
        var a=e.searchAttributeByName(name);return a==null?null:a.getValueString();
    }
    public static void main(String[] args) throws Exception {
        File apk=new File(args[0]); Path out=Path.of(args[1]);Files.createDirectories(out);
        log=new PrintWriter(Files.newBufferedWriter(out.resolve("rewrite.tsv")));
        map.put(OLD,NEW);
        ApkModule module=ApkModule.loadApkFile(apk);
        var manifest=module.getAndroidManifest();
        var nodes=elements(manifest.getManifestElement());
        for(var e:nodes) {
            if(e.getName().equals("permission") || e.getName().equals("permission-group")) add(value(e,"name"));
            String authorities=value(e,"authorities");
            if(authorities!=null) for(String a:authorities.split(";")) add(a.trim());
        }
        // Account/sync adapter types are also global identities; keep the clone separate.
        Map<String,ResXmlDocument> xmls=new LinkedHashMap<>();
        try(ZipFile z=new ZipFile(apk)) {
            var entries=z.entries();
            while(entries.hasMoreElements()) {
                String n=entries.nextElement().getName();
                if(!n.startsWith("res/xml/") || !n.endsWith(".xml"))continue;
                var doc=module.loadResXmlDocument(n);
                for(var e:elements(doc.getDocumentElement())) {
                    String type=value(e,"accountType");
                    if(type!=null) {add(type);xmls.put(n,doc);}
                }
            }
        }
        for(var e:nodes) {
            var attrs=e.getAttributes();
            while(attrs.hasNext()) {
                var a=attrs.next();String s=a.getValueString();if(s==null)continue;
                String name=a.getName();String t=s;
                // Explicitly qualify relative class references BEFORE changing package identity.
                boolean component=Set.of("application","activity","activity-alias","service","receiver","provider","instrumentation").contains(e.getName());
                boolean classAttr=(name.equals("name") && component) || Set.of("targetActivity","parentActivityName","backupAgent","appComponentFactory","manageSpaceActivity","zygotePreloadName").contains(name);
                if(classAttr) {
                    if(s.startsWith("."))t=OLD+s;
                    else if(!s.contains("."))t=OLD+"."+s;
                } else if(name.equals("authorities")) {
                    StringJoiner join=new StringJoiner(";");for(String p:s.split(";"))join.add(mapped(p.trim()));t=join.toString();
                } else if(!e.getName().equals("meta-data")) {
                    t=mapped(s);
                    if(name.equals("taskAffinity") && s.startsWith(OLD))t=NEW+s.substring(OLD.length());
                }
                if(!t.equals(s)){a.setValueAsString(t);log.println("manifest\t"+e.getName()+"@"+name+"\t"+s+"\t"+t);}
            }
        }
        manifest.setPackageName(NEW);
        // Preserve the application label resource for Facebook notification code.
        // Branding belongs on launcher components, never an inline application label.
        // Launcher components may override the application label.
        for(var e:nodes) if(Set.of("activity","activity-alias").contains(e.getName())) {
            boolean launcher=false;
            for(var child:elements(e)) if("android.intent.category.LAUNCHER".equals(value(child,"name")))launcher=true;
            if(launcher)e.getOrCreateAndroidAttribute("label",0x01010001).setValueAsString("Extended Clone");
        }
        manifest.refreshFull();manifest.writeBytes(out.resolve("AndroidManifest.xml").toFile());
        for(var entry:xmls.entrySet()) {
            var doc=entry.getValue();
            for(var e:elements(doc.getDocumentElement())) {
                var attrs=e.getAttributes();while(attrs.hasNext()) {
                    var a=attrs.next();String s=a.getValueString();String t=mapped(s);
                    if(s!=null&&!s.equals(t)){a.setValueAsString(t);log.println("xml\t"+entry.getKey()+"\t"+s+"\t"+t);}
                }
            }
            Path dest=out.resolve(entry.getKey());Files.createDirectories(dest.getParent());doc.refreshFull();doc.writeBytes(dest.toFile());
        }
        try(var m=new PrintWriter(Files.newBufferedWriter(out.resolve("identity-map.tsv")))) {
            map.forEach((a,b)->m.println(a+"\t"+b));
        }
        try(ZipFile z=new ZipFile(apk)) {
            var names=z.stream().map(ZipEntry::getName).filter(n->n.matches("classes[0-9]*\\.dex")).sorted().toList();
            for(String name:names) {
                Path input=out.resolve("input.dex");Files.copy(z.getInputStream(z.getEntry(name)),input,StandardCopyOption.REPLACE_EXISTING);
                DexFile dex=DexFileFactory.loadDexFile(input.toFile(),Opcodes.getDefault());
                // Log actual instruction/field sites before lazy rewriting.
                int count=0;
                for(var c:dex.getClasses()) {
                    for(var f:c.getFields()) if(f.getInitialValue() instanceof StringEncodedValue sv && !mapped(sv.getValue()).equals(sv.getValue())) {
                        log.println("field\t"+name+"\t"+c.getType()+"->"+f.getName()+"\t"+sv.getValue()+"\t"+mapped(sv.getValue()));count++;
                    }
                    for(var m:c.getMethods()) if(m.getImplementation()!=null)for(var ins:m.getImplementation().getInstructions()) {
                        if(ins instanceof ReferenceInstruction ri && ri.getReference() instanceof StringReference sr && !mapped(sr.getString()).equals(sr.getString())) {
                            log.println("instruction\t"+name+"\t"+c.getType()+"->"+m.getName()+"\t"+sr.getString()+"\t"+mapped(sr.getString()));count++;
                        }
                    }
                }
                if(count==0){Files.delete(input);continue;}
                var rw=new DexRewriter(new RewriterModule(){
                    @Override public Rewriter<MethodImplementation> getMethodImplementationRewriter(Rewriters rs) {
                        return impl->{
                            // DexPool re-sorts strings: use jumbo encodings so new indices cannot
                            // overflow 16 bits. MutableMethodImplementation repairs offsets/tries.
                            var mutable=new org.jf.dexlib2.builder.MutableMethodImplementation(impl);
                            var insns=mutable.getInstructions();
                            for(int i=0;i<insns.size();i++) {
                                var ins=insns.get(i);
                                if(ins instanceof ReferenceInstruction ri && ri.getReference() instanceof StringReference sr
                                        && ins instanceof OneRegisterInstruction reg) {
                                    mutable.replaceInstruction(i,new org.jf.dexlib2.builder.instruction.BuilderInstruction31c(
                                        Opcode.CONST_STRING_JUMBO,reg.getRegisterA(),new ImmutableStringReference(mapped(sr.getString()))));
                                }
                            }
                            return mutable;
                        };
                    }
                    @Override public Rewriter<Instruction> getInstructionRewriter(Rewriters rs) {
                        return ins->{
                            if(ins instanceof ReferenceInstruction ri && ri.getReference() instanceof StringReference sr) {
                                String t=mapped(sr.getString());
                                if(!t.equals(sr.getString())) {
                                    var ref=new ImmutableStringReference(t);
                                    if(ins instanceof Instruction21c i)return new ImmutableInstruction21c(ins.getOpcode(),i.getRegisterA(),ref);
                                    if(ins instanceof Instruction31c i)return new ImmutableInstruction31c(ins.getOpcode(),i.getRegisterA(),ref);
                                }
                            }
                            return ins;
                        };
                    }
                    @Override public Rewriter<EncodedValue> getEncodedValueRewriter(Rewriters rs) {
                        var parent=super.getEncodedValueRewriter(rs);
                        return v->v instanceof StringEncodedValue s ? new ImmutableStringEncodedValue(mapped(s.getValue())) : parent.rewrite(v);
                    }
                });
                DexFileFactory.writeDexFile(out.resolve(name).toString(),rw.getDexFileRewriter().rewrite(dex));
                System.out.println(name+": "+count+" identity sites");Files.delete(input);
            }
        }
        log.close();module.close();
    }
}
