"""Folder filtering must never remove rows that are not conversations."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

from test_settings_safety_runtime import method


class FolderNotificationRowsRuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_native_service_rows_survive_both_folder_filters(self):
        shape = method('ChatFolders.java', '    private static void shape(View row)')
        part = method('ChatFolders.java', '        private static List<Object> part(')
        sources = {
            'android/view/View.java': '''package android.view;public class View{
 public static int VISIBLE=0,INVISIBLE=4;public String chat;public boolean header;public int visibility;
 public ViewGroup.MarginLayoutParams params=new ViewGroup.MarginLayoutParams();java.util.Map<Integer,Object> tags=new java.util.HashMap<>();
 public Object getTag(int k){return tags.get(k);}public void setTag(int k,Object v){tags.put(k,v);}
 public ViewGroup.LayoutParams getLayoutParams(){return params;}public void setLayoutParams(ViewGroup.LayoutParams p){params=(ViewGroup.MarginLayoutParams)p;}
 public int getHeight(){return params.height;}public int getVisibility(){return visibility;}public void setVisibility(int n){visibility=n;}
}''',
            'android/view/ViewGroup.java': '''package android.view;public class ViewGroup extends View{
 public static class LayoutParams{public int height=72;}
 public static class MarginLayoutParams extends LayoutParams{public int topMargin=3,bottomMargin=4;}
}''',
            'Test.java': '''import android.view.*;import java.util.*;
public class Test{
 static final int TAG_HIDDEN=1;static String active="work";static long settleUntil;
 static class Folder{Map<String,String> chats=new HashMap<>();}
 static Folder selected=new Folder();static Folder folder(String id){return selected;}
 static String keyOf(View row){return row.chat;}static boolean header(View row){return row.header;}
 static class Narrowed{static boolean on(){return false;}}
 static class Model{String chat;Model(String name){chat=name;}}
 static String chatName(Object wrapper){return ((Model)wrapper).chat;}
 static boolean holds(String id,String name){return selected.chats.containsKey(name);}
 static void ck(boolean value,String message){if(!value)throw new AssertionError(message);}
''' + shape + part + '''
 public static void main(String[] args)throws Exception{
  selected.chats.put("wanted","kept");
  View activity=new View(),system=new View(),unknownService=new View(),wanted=new View(),excluded=new View();wanted.chat="wanted";excluded.chat="other";
  for(int i=0;i<40;i++)for(View row:new View[]{activity,system,unknownService,wanted,excluded})shape(row);
  for(View row:new View[]{activity,system,unknownService,wanted})ck(row.params.height==72&&row.visibility==View.VISIBLE&&row.params.topMargin==3&&row.params.bottomMargin==4,"native service rows and selected chats keep their geometry");
  ck(excluded.params.height==0,"unselected conversations are still filtered");
  system.setTag(TAG_HIDDEN,new int[]{72,3,4,View.VISIBLE});system.params.height=0;system.visibility=View.INVISIBLE;shape(system);
  ck(system.params.height==72&&system.visibility==View.VISIBLE&&system.getTag(TAG_HIDDEN)==null,"previously hidden service row is restored");
  Model a=new Model(null),s=new Model(null),u=new Model(null),keep=new Model("wanted"),drop=new Model("other");
  List<Object> out=part(active,Arrays.asList(a,s,u,keep,drop,null));
  ck(out.size()==4&&out.get(0)==a&&out.get(1)==s&&out.get(2)==u&&out.get(3)==keep,"model filtering preserves every native service row in order");
 }
}''',
        }
        with tempfile.TemporaryDirectory() as folder:
            paths = []
            for name, content in sources.items():
                path = pathlib.Path(folder) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                paths.append(str(path))
            built = subprocess.run(['javac', '-d', folder, *paths], capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            ran = subprocess.run(['java', '-cp', folder, 'Test'], capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)
