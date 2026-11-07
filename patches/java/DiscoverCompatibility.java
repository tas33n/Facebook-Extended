import java.io.*;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;

/** Static fingerprint inventory from actual const-string uses, not string presence. */
public class DiscoverCompatibility {
 public static void main(String[] args)throws Exception {
  Set<String> anchors=Set.of("FbGrootPlayer.pause","ProfileTopHeaderComponent","low_res_profile_picture_uri","profile_cover_photo_click");
  var container=DexFileFactory.loadDexContainer(new File(args[0]),Opcodes.getDefault());
  for(String name:container.getDexEntryNames())for(var c:container.getEntry(name).getDexFile().getClasses()){
   for(var m:c.getMethods())if(m.getImplementation()!=null)for(var i:m.getImplementation().getInstructions()){
    if(i instanceof ReferenceInstruction r && r.getReference() instanceof StringReference s && anchors.contains(s.getString())) {
     System.out.println("ANCHOR\t"+s.getString()+"\t"+name+"\t"+c.getType()+"\t"+m.getName());
    }
   }
  }
 }
}
