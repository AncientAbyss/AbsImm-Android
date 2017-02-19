package net.ancientabyss.absimm;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.jivesoftware.smack.SmackException;

import java.io.InputStream;

import at.absoluteimmersion.core.Loader;
import at.absoluteimmersion.core.ReactionClient;
import at.absoluteimmersion.core.Story;

public class GameActivity extends AppCompatActivity implements ReactionClient {

    private LinearLayout mainLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        mainLayout = (LinearLayout) findViewById(R.id.main_layout);
        initInputHandling(initStory());
    }

    private void initInputHandling(final Story story) {
        Button button = (Button) findViewById(R.id.input_button);
        final EditText text = (EditText) findViewById(R.id.edit_message);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    String interaction = text.getText().toString();
                    addText(">> " + interaction);
                    story.interact(interaction);
                } catch (Exception e) {
                    System.err.println("Failed to interact: " + e.getMessage());
                }
                text.setText("");
            }
        });
    }

    private Story initStory() {
        Story story = null;
        try {
            Resources res = getResources();
            InputStream in = res.openRawResource(R.raw.demo);
            byte[] b = new byte[in.available()];
            in.read(b);
            story = new Loader().fromString(new String(b));
        } catch (Exception e) {
            System.err.println("Failed loading story: " + e.getMessage());
        }
        story.addClient(this);
        try {
            story.tell();
        } catch (Exception e) {
            System.err.println("Failed telling story: " + e.getMessage());
        }
        return story;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_game, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void reaction(String s) throws SmackException.NotConnectedException {
        addText(s);
    }

    private void addText(String s) {
        TextView textView = new TextView(this);
        textView.setText(s.replace("\\n", "\r\n"));
        mainLayout.addView(textView);
    }
}
