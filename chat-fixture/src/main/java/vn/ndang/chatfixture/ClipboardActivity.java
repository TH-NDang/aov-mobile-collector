package vn.ndang.chatfixture;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** CI-only receiver in a different UID: read an image as a real paste target would. */
public class ClipboardActivity extends Activity {
    private boolean attempted;
    private TextView label;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);label=new TextView(this);label.setText("Đang chờ dán ảnh");setContentView(label);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(!focused||attempted) return;attempted=true;
        try {
            ClipData clip=((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).getPrimaryClip();
            if(clip==null||clip.getItemCount()!=1||clip.getItemAt(0).getUri()==null) throw new Exception("No image URI");
            try(InputStream input=getContentResolver().openInputStream(clip.getItemAt(0).getUri());
                FileOutputStream output=new FileOutputStream(new File(getFilesDir(),"pasted-image.bin"))) {
                if(input==null) throw new Exception("No image stream");
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))>=0) output.write(buffer,0,count);
            }
            label.setText("Đã dán ảnh thử");
        } catch(Exception e) { label.setText("Dán ảnh thất bại: "+e); }
    }
}
