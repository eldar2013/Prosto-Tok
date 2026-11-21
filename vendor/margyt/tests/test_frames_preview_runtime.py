"""Exercise the actual frame preview with shape changes and a rotating mask."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

from test_settings_safety_runtime import method

ROOT = pathlib.Path(__file__).resolve().parents[1]


class FramesPreviewRuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_selected_shape_frame_and_stationary_photo(self):
        frame = method('Frames.java', '    public static final class Frame {')
        preview = method('Frames.java', '    static final class Preview extends View {')
        sources = {
            'android/content/Context.java': 'package android.content;public class Context{}',
            'android/view/View.java': '''package android.view;public class View{
 public int width=320,height=190;android.content.Context context;
 public View(android.content.Context c){context=c;}public android.content.Context getContext(){return context;}
 public int getWidth(){return width;}public int getHeight(){return height;}public void invalidate(){}
 public void postDelayed(Runnable r,long ms){}protected void onDraw(android.graphics.Canvas c){}
}''',
            'android/graphics/Rect.java': '''package android.graphics;public class Rect{
 public int left,top,right,bottom;public void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
}''',
            'android/graphics/Matrix.java': '''package android.graphics;public class Matrix{
 public float sx,sy,x,y,angle;public void setScale(float a,float b){sx=a;sy=b;x=y=angle=0;}
 public void postTranslate(float a,float b){x+=a;y+=b;}public void postRotate(float a,float x,float y){angle+=a;}
}''',
            'android/graphics/Path.java': '''package android.graphics;public class Path{
 public String shape;public float angle;public void rewind(){shape=null;angle=0;}
 public void addPath(Path p,Matrix m){shape=p.shape;angle=m.angle;}
}''',
            'android/graphics/Shader.java': '''package android.graphics;public class Shader{
 public enum TileMode{CLAMP}public Matrix matrix;public void setLocalMatrix(Matrix m){matrix=m;}
}''',
            'android/graphics/BitmapShader.java': 'package android.graphics;public class BitmapShader extends Shader{public BitmapShader(Bitmap b,TileMode a,TileMode c){}}',
            'android/graphics/Paint.java': '''package android.graphics;public class Paint{
 public static int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;Shader shader;
 public Paint(int flags){}public void setColor(int c){}public void setShader(Shader s){shader=s;}public Shader getShader(){return shader;}
}''',
            'android/graphics/Bitmap.java': '''package android.graphics;public class Bitmap{
 public String name;int width,height;public Bitmap(String n,int w,int h){name=n;width=w;height=h;}
 public int getWidth(){return width;}public int getHeight(){return height;}
}''',
            'android/graphics/Canvas.java': '''package android.graphics;public class Canvas{
 public String mask,bitmap,clipped;public float maskAngle,bitmapAngle,shaderAngle,angle,cx,cy;
 public boolean noteClipped;public double minX,minY,maxX,maxY;
 public int save(){return 1;}public void restoreToCount(int n){angle=0;clipped=null;}
 public void rotate(float a,float x,float y){angle=a;cx=x;cy=y;}
 public boolean clipPath(Path p){clipped=p.shape;return true;}
 public void drawCircle(float x,float y,float r,Paint p){throw new AssertionError("hardcoded circle");}
 public void drawPath(Path path,Paint p){mask=path.shape;maskAngle=path.angle;shaderAngle=p.getShader()==null?0:p.getShader().matrix.angle;}
 public void drawBitmap(Bitmap b,Rect from,Rect to,Paint p){
  bitmap=b.name;bitmapAngle=angle;if(b.name.equals("note"))noteClipped=clipped!=null;
  minX=minY=Double.POSITIVE_INFINITY;maxX=maxY=Double.NEGATIVE_INFINITY;
  double a=Math.toRadians(angle),co=Math.cos(a),si=Math.sin(a);
  for(int x:new int[]{to.left,to.right})for(int y:new int[]{to.top,to.bottom}){
   double px=cx+(x-cx)*co-(y-cy)*si,py=cy+(x-cx)*si+(y-cy)*co;
   minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
  }
 }
}''',
            'cat/narezany/margyt/AvatarShape.java': '''package cat.narezany.margyt;class AvatarShape{
 static String shape="circle";static boolean spin;static int redraws;
 static String chosen(){return shape;}static boolean turning(){return spin;}static float angle(){return 37;}
 static android.graphics.Path chosenUnit(){android.graphics.Path p=new android.graphics.Path();p.shape=shape;return p;}
 static void again(Object who){redraws++;}
}''',
            'cat/narezany/margyt/Badges.java': 'package cat.narezany.margyt;class Badges{static android.graphics.Bitmap note(){return new android.graphics.Bitmap("note",64,64);}}',
            'cat/narezany/margyt/Frames.java': '''package cat.narezany.margyt;
import android.content.Context;import android.view.View;import android.graphics.*;import java.util.Map;
public class Frames{
 static Bitmap own=new Bitmap("face",400,200);
 static Bitmap face(View v){return own;}
 static Bitmap picture(Context c,String name){return new Bitmap(name,512,512);}
 static float[] covers(Frame frame,Bitmap art){return new float[]{0,0,1,1};}
''' + frame + preview + '}',
            'cat/narezany/margyt/Check.java': '''package cat.narezany.margyt;
import android.graphics.*;import java.util.*;
public class Check{
 static void ck(boolean v,String s){if(!v)throw new AssertionError(s);}
 public static void main(String[] args){
  String[] shapes={"circle","rounded","square","cookie","clover","burst","sun","pentagon","hexagon","heart"};
  Map<String,String> pictures=new HashMap<>();for(String shape:shapes)pictures.put(shape,"glow_"+shape);
  Frames.Frame frame=new Frames.Frame("supporter","default",1.55f,0,"","",pictures);
  Frames.Preview preview=new Frames.Preview(new android.content.Context(),0);
  preview.set(frame);
  for(int size:new int[]{40,320})for(String shape:shapes)for(boolean spin:new boolean[]{false,true})for(float offset:new float[]{0,.25f,-.25f}){
   preview.set(new Frames.Frame("supporter","default",1.55f,offset,"","",pictures));
   preview.width=size;preview.height=size==40?40:190;AvatarShape.shape=shape;AvatarShape.spin=spin;
   Canvas canvas=new Canvas();int redraws=AvatarShape.redraws;preview.onDraw(canvas);
   ck(shape.equals(canvas.mask),"preview avatar follows selected "+shape);
   ck(("glow_"+shape).equals(canvas.bitmap),"frame artwork follows selected "+shape);
   ck(canvas.maskAngle==(spin?37:0)&&canvas.bitmapAngle==(spin?37:0),"mask and frame rotate together");
   ck(canvas.shaderAngle==0,"the photo does not rotate");
   ck(AvatarShape.redraws==redraws+(spin?1:0),"only spinning previews request animation");
   ck(canvas.minX>=-1&&canvas.minY>=-1&&canvas.maxX<=preview.width+1&&canvas.maxY<=preview.height+1,"full rotating frame fits the showcase");
  }
  Frames.own=null;AvatarShape.spin=false;preview.onDraw(new Canvas());Canvas empty=new Canvas();preview.onDraw(empty);
  ck(empty.noteClipped,"fallback icon is clipped to the selected avatar shape");
  pictures.remove(AvatarShape.shape);ck("default".equals(frame.picture()),"missing artwork falls back to default");
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
            ran = subprocess.run(['java', '-cp', folder, 'cat.narezany.margyt.Check'], capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)
