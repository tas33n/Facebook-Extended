import com.reandroid.apk.ApkModule;
import java.io.File;
public class AddRestartManifest {
 public static void main(String[] args)throws Exception {
  var apk=ApkModule.loadApkFile(new File(args[0]));
  var m=apk.getAndroidManifest();
  var a=m.getOrCreateActivity("tn.loukious.fbar.embedded.RestartActivity",false);
  a.getOrCreateAndroidAttribute("exported",0x01010010).setValueAsBoolean(false);
  a.getOrCreateAndroidAttribute("process",0x01010011).setValueAsString(":fbar_restart");
  a.getOrCreateAndroidAttribute("excludeFromRecents",0x01010017).setValueAsBoolean(true);
  a.getOrCreateAndroidAttribute("taskAffinity",0x01010012).setValueAsString(m.getPackageName()+".extended.restart");
  String label=m.getPackageName().equals("com.facebook.katana") ? "Facebook Extended" : "Extended Clone";
  // Facebook notification code calls Resources.getString(ApplicationInfo.labelRes).
  // Keep the original resource reference: an inline label makes labelRes zero.
  var applicationLabel=m.getApplicationElement().searchAttributeByName("label");
  if(applicationLabel==null || applicationLabel.getValueType()!=com.reandroid.arsc.value.ValueType.REFERENCE || applicationLabel.getData()==0)
   throw new IllegalStateException("Application label must remain a nonzero resource reference");
  for(var entry:m.listActivities()) {
   var nodes=entry.recursiveElements();boolean launcher=false;
   while(nodes.hasNext()) {
    var e=(com.reandroid.arsc.chunk.xml.ResXmlElement)nodes.next();var n=e.searchAttributeByName("name");
    if(n!=null && "android.intent.category.LAUNCHER".equals(n.getValueString()))launcher=true;
   }
   if(launcher)entry.getOrCreateAndroidAttribute("label",0x01010001).setValueAsString(label);
  }
  var theme=a.getOrCreateAndroidAttribute("theme",0x01010000);
  theme.setValueType(com.reandroid.arsc.value.ValueType.REFERENCE);theme.setData(0x01030241);
  m.refreshFull();m.writeBytes(new File(args[1]));apk.close();
 }
}
