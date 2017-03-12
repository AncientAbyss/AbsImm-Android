package net.ancientabyss.absimm;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import net.ancientabyss.absimm.models.Author;
import net.ancientabyss.absimm.models.Message;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.SmackException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import at.absoluteimmersion.core.Loader;
import at.absoluteimmersion.core.ReactionClient;
import at.absoluteimmersion.core.Story;

import static org.apache.commons.lang3.StringUtils.INDEX_NOT_FOUND;

public class GameActivity extends AppCompatActivity implements ReactionClient {

    private static final Author botAuthor = new Author("absimm", "absimm", "");
    private static final Author userAuthor = new Author("user", "user", "");
    private static final String prefsName = "absimm-state";
    private static final String statePrefName = "state";

    private List<String> commands = new ArrayList<>();
    private MessagesListAdapter<IMessage> adapter;

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

        MessagesList messagesList = (MessagesList) findViewById(R.id.messagesList);
        adapter = new MessagesListAdapter<>(userAuthor.getId(), null);
        messagesList.setAdapter(adapter);
        Story story = initStory();
        initInputHandling(story);

        restoreState(story);
    }

    private void restoreState(Story story) {
        SharedPreferences settings = getSharedPreferences(prefsName, 0);
        String state = settings.getString(statePrefName, "");
        String[] commands = StringUtils.split(state, '\t');
        for (String command : commands) {
            interact(command, story);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        saveState();
    }

    private void saveState() {
        SharedPreferences settings = getSharedPreferences(prefsName, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(statePrefName, StringUtils.join(commands, '\t'));
        editor.apply();
    }

    private void initInputHandling(final Story story) {
        final MessageInput input = (MessageInput) findViewById(R.id.input);

        input.getInputEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int count, int before) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String inputText = input.getInputEditText().getText().toString();
                if (StringUtils.indexOf(inputText, '\n') == INDEX_NOT_FOUND) return;
                String[] lines = StringUtils.split(inputText, '\n');
                if (lines.length < 1) {
                    if (inputText.length() > 0) input.getInputEditText().setText("");
                    return;
                }
                interact(lines[0], story);
                input.getInputEditText().setText("");
            }
        });

        input.setInputListener(new MessageInput.InputListener() {
            @Override
            public boolean onSubmit(CharSequence input) {
                String interaction = input.toString();
                interact(interaction, story);
                return true;
            }
        });
    }

    private void interact(String interaction, Story story) {
        try {
            addText(interaction, userAuthor);
            commands.add(interaction);
            story.interact(interaction.toLowerCase());
        } catch (Exception e) {
            System.err.println("Failed to interact: " + e.getMessage());
        }
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
    public void reaction(String text) throws SmackException.NotConnectedException {
        addText(text, botAuthor);
    }

    private void addText(String text, Author author) {
        String message = text.replace("\\n", "\r\n");
        adapter.addToStart(new Message(UUID.randomUUID().toString(), message, author, new Date()), true);
    }
}
