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
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import net.ancientabyss.absimm.core.Loader;
import net.ancientabyss.absimm.core.ReactionClient;
import net.ancientabyss.absimm.core.Story;
import net.ancientabyss.absimm.core.StoryException;
import net.ancientabyss.absimm.models.Author;
import net.ancientabyss.absimm.models.Message;
import net.ancientabyss.absimm.parser.TxtParser;

import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.INDEX_NOT_FOUND;

public class GameActivity extends AppCompatActivity implements ReactionClient {

    private static final Author botAuthor = new Author("absimm", "absimm", "");
    private static final Author userAuthor = new Author("user", "user", "");
    private static final String prefsName = "absimm-state";
    private static final String statePrefName = "state";
    private static final String defaultErrorMessage = "Whoops, something went wrong! Please let us know what you were doing (feedback@ancientabyss.net)!";

    private List<String> commands = new ArrayList<>();
    private MessagesListAdapter<IMessage> adapter;
    private MessagesList messagesList;
    private Story story;
    private SparseArray<Runnable> optionDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());

        messagesList = (MessagesList) findViewById(R.id.messagesList);
        adapter = new MessagesListAdapter<>(userAuthor.getId(), null);
        messagesList.setAdapter(adapter);
        story = initStory();
        initInputHandling(story);
        initOptions();

        restoreState(story);

        if (commands.isEmpty()) {
            messagesList.scrollToPosition(1);
        }
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

        input.setInputListener(input1 -> {
            String interaction = input1.toString();
            interact(interaction, story);
            return true;
        });
    }

    private void interact(String interaction, Story story) {
        try {
            commands.add(interaction);
            addText(interaction, userAuthor, false);
            messagesList.scrollToPosition(2);
            story.interact(interaction.toLowerCase());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            showError(defaultErrorMessage);
        }
    }

    private Story initStory() {
        Story story = null;
        try {
            Resources res = getResources();
            InputStream in = res.openRawResource(R.raw.demo_txt);
            byte[] b = new byte[in.available()];
            //noinspection ResultOfMethodCallIgnored
            in.read(b);
            story = new Loader(new TxtParser()).fromString(new String(b));
            story.addClient(this);
            story.tell();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            showError(defaultErrorMessage);
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

        optionDispatcher.get(id).run();

        return super.onOptionsItemSelected(item);
    }

    private void initOptions() {
        this.optionDispatcher = new SparseArray<>();
        optionDispatcher.put(R.id.action_reset, this::reset);
        optionDispatcher.put(R.id.action_settings, this::showSettings);
        optionDispatcher.put(R.id.action_hint, this::hint);
    }

    private void showSettings() {
        showError("Not yet available!");
    }

    private boolean reset() {
        //messagesList.scrollTo(0, 0);
        commands.clear();
        story.setState("");
        adapter.clear();
        adapter.notifyDataSetChanged();
        try {
            story.tell();
        } catch (StoryException e) {
            System.err.println(e.getMessage());
            showError(defaultErrorMessage);
        }
        return true;
    }

    private void hint() {
        interact("hint", story);
    }

    @Override
    public void reaction(String text) {
        addText(text, botAuthor, shouldScroll());
    }

    private boolean shouldScroll() {
        return !commands.isEmpty(); // do not scroll for initial text
    }

    private synchronized void addText(String text, Author author, Boolean scroll) {
        String message = text.replace("\\n", "\r\n");
        adapter.addToStart(new Message(UUID.randomUUID().toString(), message, author, new Date()), false);
        if (scroll) {
            messagesList.smoothScrollBy(0, messagesList.getHeight()); // scroll to the top of the message, not the bottom as does addToStart(.., true)
        }
        if (!shouldScroll() && messagesList.getChildAt(0) != null) {
            messagesList.smoothScrollToPosition(1);
        }
    }

    private void showError(String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }
}
