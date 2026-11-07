import java.io.*;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;

/** Validate typed member chains and behavioral anchors before compiling host mappings. */
public class ValidateCompatibility {
 static Map<String,ClassDef> classes=new HashMap<>();
 static String type(String n){return "L"+n.replace('.','/')+";";}
 static ClassDef cls(String n){var c=classes.get(n);if(c==null)throw new IllegalStateException("Missing class "+n);return c;}
 static Method method(String owner,String name,int count){
  List<Method> matches=new ArrayList<>(); collect(cls(owner),name,count,matches,new HashSet<>());
  if(matches.isEmpty())throw new IllegalStateException("Missing method "+owner+"."+name+"/"+count);
  Set<String> returns=new HashSet<>();for(var m:matches)returns.add(m.getReturnType());
  if(returns.size()!=1)throw new IllegalStateException("Ambiguous return shape "+owner+"."+name);
  return matches.get(0);
 }
 static void collect(ClassDef c,String name,int count,List<Method> out,Set<String> seen){
  if(!seen.add(c.getType()))return;
  for(var m:c.getMethods())if(m.getName().equals(name)&&m.getParameterTypes().size()==count)out.add(m);
  if(!out.isEmpty())return;
  for(String i:c.getInterfaces())if(classes.containsKey(i))collect(cls(i),name,count,out,seen);
  if(classes.containsKey(c.getSuperclass()))collect(cls(c.getSuperclass()),name,count,out,seen);
 }
 static Field field(String owner,String name){for(var f:cls(owner).getFields())if(f.getName().equals(name))return f;throw new IllegalStateException("Missing field "+owner+"."+name);}
 static boolean anchor(ClassDef c,String text){
  for(var m:c.getMethods())if(m.getImplementation()!=null)for(var i:m.getImplementation().getInstructions())
   if(i instanceof ReferenceInstruction r && r.getReference() instanceof StringReference s && s.getString().equals(text))return true;
  return false;
 }
 public static void main(String[] a)throws Exception {
  var archive=DexFileFactory.loadDexContainer(new File(a[0]),Opcodes.getDefault());
  for(String name:archive.getDexEntryNames())for(var c:archive.getEntry(name).getDexFile().getClasses())classes.put(c.getType(),c);
  String player=type(a[1]),header=type(a[2]),helper=type(a[6]);
  if(!anchor(cls(player),"FbGrootPlayer.pause"))throw new IllegalStateException("Player anchor changed");
  if(!anchor(cls(header),"low_res_profile_picture_uri"))throw new IllegalStateException("Header anchor changed");
  if(!method(player,"isPlaying",0).getReturnType().equals("Z"))throw new IllegalStateException("isPlaying not boolean");
  method(player,"maybeTrackVideoStart",1);method(player,"maybeTrackVideoStop",1);
  int getters=0;for(var m:cls(player).getMethods())if(m.getParameterTypes().isEmpty()&&m.getReturnType().equals("Lcom/facebook/video/engine/api/VideoPlayerParams;"))getters++;
  if(getters!=1)throw new IllegalStateException("Player params getter not unique");
  method(header,"render",1);
  String owner=field(header,a[3]).getType();String model=method(owner,a[5],0).getReturnType();
  if(!field(header,a[4]).getType().equals("Ljava/lang/String;"))throw new IllegalStateException("Preview field not string");
  Method image=method(helper,a[7],1);
  if(!image.getReturnType().equals("Ljava/lang/String;")||!image.getParameterTypes().get(0).toString().equals(model))throw new IllegalStateException("Image helper model mismatch");
  String current=model;for(String name:a[8].split(","))current=method(current,name,0).getReturnType();
  if(!current.equals("Ljava/lang/String;"))throw new IllegalStateException("Cover path not URI string");
  System.out.println("PASS player anchor + lifecycle + params getter; header anchor + render; typed model/image/cover chains");
  System.out.println("Coverage: mapped members/anchors only; not runtime behavior or all upstream feature discovery");
 }
}
