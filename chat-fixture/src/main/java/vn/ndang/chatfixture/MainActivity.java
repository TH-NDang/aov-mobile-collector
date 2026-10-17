package vn.ndang.chatfixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** No network or accounts. Exercises navigation and sending in a separate application's real UI. */
public class MainActivity extends Activity {
    private LinearLayout body;
    @Override public void onCreate(Bundle state) { super.onCreate(state);if(new File(getFilesDir(),"sent.txt").exists()) dot();else home(); }
    private void page(String heading) {
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(24,80,24,24);
        TextView title=new TextView(this);title.setText(heading);title.setTextSize(24);body.addView(title);setContentView(body);
    }
    private void button(String label,Runnable action) {
        Button button=new Button(this);button.setText(label);button.setContentDescription(label);
        button.setOnClickListener(view -> action.run());body.addView(button);
    }
    private void home() {
        page("ChatGPT");button("Open sidebar",this::drawer);
        EditText wrong=new EditText(this);wrong.setHint("Ordinary conversation");body.addView(wrong);
    }
    private void drawer() {
        page("ChatGPT");button("dot của bạn",this::dot);button("Đã lên lịch",()->{});
        button("Không gian",()->{});button("Tùy chỉnh",()->{});
    }
    private void dot() {
        page("dot của bạn");EditText editor=new EditText(this);editor.setHint("Message");body.addView(editor);
        button("Send prompt",()->{
            String message=editor.getText().toString();if(message.isEmpty()) return;
            TextView sent=new TextView(this);sent.setText(message);body.addView(sent);editor.setText("");
            try { Files.write(new File(getFilesDir(),"sent.txt").toPath(),("dot\n"+message).getBytes(StandardCharsets.UTF_8)); }
            catch(Exception e) { throw new RuntimeException(e); }
        });
    }
}
