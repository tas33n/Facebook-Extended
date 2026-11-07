import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.instruction.formats.*;
import org.jf.dexlib2.iface.reference.*;
import org.jf.dexlib2.iface.value.*;

/** Compare original and rewritten method semantics, normalizing instruction widths. */
public class VerifyCloneDex {
 static Map<String,String> replacements=new HashMap<>();
 static String map(String s){
  if(replacements.containsKey(s))return replacements.get(s);
  for(var e:replacements.entrySet()){
   String p="content://"+e.getKey();
   if(s.equals(p)||s.startsWith(p+"/")||s.startsWith(p+"?"))return "content://"+e.getValue()+s.substring(p.length());
  }
  if(s.startsWith("com.facebook.katana:"))return "com.tas33n.facebook.extended"+s.substring("com.facebook.katana".length());
  return s;
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static String ref(Reference r,boolean old){return r instanceof StringReference s ? (old?map(s.getString()):s.getString()) : r.toString();}
 static List<String> code(MethodImplementation m,boolean old){
  if(m==null)return List.of("no-code");
  List<Instruction> list=new ArrayList<>();List<Integer> addresses=new ArrayList<>();Map<Integer,Integer> indices=new HashMap<>();
  int pc=0,index=0;
  for(var i:m.getInstructions()){addresses.add(pc);list.add(i);indices.put(pc,index);if(i.getOpcode()!=Opcode.NOP)index++;pc+=i.getCodeUnits();}
  indices.put(pc,index);
  Map<Integer,Integer> payloadOwners=new HashMap<>();
  for(int j=0;j<list.size();j++){var i=list.get(j);if(i.getOpcode()==Opcode.PACKED_SWITCH||i.getOpcode()==Opcode.SPARSE_SWITCH)payloadOwners.put(addresses.get(j)+((OffsetInstruction)i).getCodeOffset(),addresses.get(j));}
  List<String> out=new ArrayList<>();out.add("registers:"+m.getRegisterCount());
  for(int j=0;j<list.size();j++){
   var i=list.get(j);if(i.getOpcode()==Opcode.NOP)continue;
   String op=i.getOpcode().toString();
   if(i.getOpcode()==Opcode.CONST_STRING_JUMBO)op="CONST_STRING";
   if(i.getOpcode()==Opcode.GOTO_16||i.getOpcode()==Opcode.GOTO_32)op="GOTO";
   StringBuilder s=new StringBuilder(op);
   if(i instanceof OneRegisterInstruction x)s.append(" A=").append(x.getRegisterA());
   if(i instanceof TwoRegisterInstruction x)s.append(" B=").append(x.getRegisterB());
   if(i instanceof ThreeRegisterInstruction x)s.append(" C=").append(x.getRegisterC());
   if(i instanceof FiveRegisterInstruction x)s.append(" regs=").append(Arrays.asList(x.getRegisterCount(),x.getRegisterC(),x.getRegisterD(),x.getRegisterE(),x.getRegisterF(),x.getRegisterG()));
   if(i instanceof RegisterRangeInstruction x)s.append(" range=").append(x.getStartRegister()).append(":").append(x.getRegisterCount());
   if(i instanceof WideLiteralInstruction x)s.append(" literal=").append(x.getWideLiteral());
   if(i instanceof ReferenceInstruction x)s.append(" ref=").append(ref(x.getReference(),old));
   if(i instanceof DualReferenceInstruction x)s.append(" ref2=").append(ref(x.getReference2(),old));
   if(i instanceof OffsetInstruction x){Integer target=indices.get(addresses.get(j)+x.getCodeOffset());check(target!=null,"bad branch target");s.append(" target=").append(target);}
   if(i instanceof SwitchPayload x){Integer owner=payloadOwners.get(addresses.get(j));check(owner!=null,"missing switch owner");for(var e:x.getSwitchElements())s.append(" case=").append(e.getKey()).append(":").append(indices.get(owner+e.getOffset()));}
   if(i instanceof ArrayPayload x)s.append(" array=").append(x.getElementWidth()).append(":").append(x.getArrayElements());
   out.add(s.toString());
  }
  for(var t:m.getTryBlocks()){
   List<String> handlers=new ArrayList<>();
   Set<String> seen=new HashSet<>();
   for(var h:t.getExceptionHandlers())if(seen.add(h.getExceptionType()))handlers.add("catch:"+h.getExceptionType()+":"+indices.get(h.getHandlerCodeAddress()));
   for(int k=indices.get(t.getStartCodeAddress());k<indices.get(t.getStartCodeAddress()+t.getCodeUnitCount());k++)out.add("try:"+k+":"+handlers);
  }
  return out;
 }
 public static void main(String[] args)throws Exception{
  for(String line:Files.readAllLines(Path.of(args[2]))){var p=line.split("\t");replacements.put(p[0],p[1]);}
  var a=DexFileFactory.loadDexFile(new File(args[0]),Opcodes.getDefault());
  var b=DexFileFactory.loadDexFile(new File(args[1]),Opcodes.getDefault());
  Map<String,ClassDef> classes=new HashMap<>();for(var c:b.getClasses())classes.put(c.getType(),c);
  check(a.getClasses().size()==classes.size(),"class count");int methods=0;
  for(var c:a.getClasses()){
   var d=classes.get(c.getType());check(d!=null,"class missing");check(Objects.equals(c.getSuperclass(),d.getSuperclass())&&c.getInterfaces().equals(d.getInterfaces())&&c.getAccessFlags()==d.getAccessFlags(),"class shape");
   Map<String,Field> fields=new HashMap<>();for(var f:d.getFields())fields.put(f.toString(),f);
   int fieldCount=0;for(var f:c.getFields()){fieldCount++;var g=fields.get(f.toString());check(g!=null&&f.getAccessFlags()==g.getAccessFlags(),"field shape");var v=f.getInitialValue();var w=g.getInitialValue();check(v instanceof StringEncodedValue sv ? w instanceof StringEncodedValue sw&&map(sv.getValue()).equals(sw.getValue()) : Objects.equals(v,w),"field value");}check(fieldCount==fields.size(),"field count");
   Map<String,Method> ms=new HashMap<>();for(var m:d.getMethods())ms.put(m.toString(),m);
   int mc=0;for(var m:c.getMethods()){mc++;var n=ms.get(m.toString());check(n!=null&&m.getAccessFlags()==n.getAccessFlags(),"method shape");var x=code(m.getImplementation(),true);var y=code(n.getImplementation(),false);if(!x.equals(y)){Files.write(Path.of(args[1]+".old.txt"),x);Files.write(Path.of(args[1]+".new.txt"),y);}check(x.equals(y),"code mismatch "+m+" old size="+x.size()+" new="+y.size());methods++;}check(mc==ms.size(),"method count");
  }
  System.out.println("PASS classes="+classes.size()+" methods="+methods);
 }
}
