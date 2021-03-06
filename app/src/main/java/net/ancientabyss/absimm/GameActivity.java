package net.ancientabyss.absimm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import net.ancientabyss.absimm.core.ReactionClient;
import net.ancientabyss.absimm.core.Story;
import net.ancientabyss.absimm.core.StoryException;
import net.ancientabyss.absimm.loader.StringLoader;
import net.ancientabyss.absimm.models.Author;
import net.ancientabyss.absimm.models.Message;
import net.ancientabyss.absimm.models.Statistics;
import net.ancientabyss.absimm.parser.TxtParser;
import net.ancientabyss.absimm.utils.KeyboardUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class GameActivity extends AppCompatActivity implements ReactionClient {

    private static final Author botAuthor = new Author("absimm", "absimm", "");
    private static final Author userAuthor = new Author("user", "user", "");
    private static final String prefsName = "absimm-state";
    private static final String statePrefName = "state";
    private static final String datesPrefName = "dates";
    private static final String themePrefName = "pref_theme";
    private static final String defaultErrorMessage = "Whoops, something went wrong! Please let us know what you were doing (feedback@ancientabyss.net)!";

    private List<String> commands = new ArrayList<>();
    private List<Long> commandExecutionTimes = new ArrayList<>();
    private MessagesListAdapter<IMessage> adapter;
    private MessagesList messagesList;
    private Story story;
    private SparseArray<Runnable> optionDispatcher;
    private Date restoreDate;
    private boolean isFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        KeyboardUtil keyboardUtil = new KeyboardUtil(this, findViewById(android.R.id.content));
        keyboardUtil.enable();

        setTheme(false);
        setContentView(R.layout.activity_game);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> Snackbar.make(view, "Visit us at ancientabyss.net!", Snackbar.LENGTH_LONG)
                .setAction(R.string.fab_action, view1 -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://ancientabyss.net"));
                    startActivity(browserIntent);
                }).show());

        initThemableViews();
        messagesList = findViewById(getCurrentThemeIndex() == 1 ? R.id.messagesList2 : R.id.messagesList);
        adapter = new MessagesListAdapter<>(userAuthor.getId(), null);
        messagesList.setAdapter(adapter);

        isFinished = false;

        if (shouldStateBeRestored()) {
            restoreDate = getInitialRestoreDate();
        }

        story = initStory();
        initInputHandling(story);
        initOptions();

        restoreState(story);

        restoreDate = null;

        if (commands.isEmpty()) {
            messagesList.scrollToPosition(1);
        }
    }

    private void initThemableViews() {
        int[][] themables = {
                {R.id.messagesList, R.id.messagesList2},
                {R.id.input, R.id.input2}
        };
        for (int[] viewVariations : themables) {
            for (int i = 0; i < viewVariations.length; ++i) {
                findViewById(viewVariations[i]).setVisibility(getCurrentThemeIndex() == i ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void setTheme(boolean recreate) {
        setTheme(getCurrentTheme());
        if (recreate) {
            recreate();
        }
    }

    private int getCurrentTheme() {
        switch (getCurrentThemeIndex()) {
            case 1: return R.style.AppTheme2_NoActionBar;
            default: return R.style.AppTheme_NoActionBar;
        }
    }

    private int getCurrentThemeIndex() {
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(themePrefName, "0"));
    }

    private void restoreState(Story story) {
        String[] commands = getRestoreCommands();
        String[] dates = getRestoreDates();
        for (int i = 0; i < commands.length; ++i) {
            Date date = new Date(Long.parseLong(dates[i]));
            restoreDate = date;
            interact(commands[i], story, date);
        }
    }

    private boolean shouldStateBeRestored() {
        return getRestoreCommands().length > 0;
    }

    private String[] getRestoreCommands() {
        return TextUtils.split(getSharedPreferences(prefsName, 0).getString(statePrefName, ""), "\t");
    }

    private String[] getRestoreDates() {
        return TextUtils.split(getSharedPreferences(prefsName, 0).getString(datesPrefName, ""), "\t");
    }

    private Date getInitialRestoreDate() {
        String[] dates = getRestoreDates();
        return (dates.length > 1) ? new Date(Long.parseLong(dates[0])) : new Date();
    }

    @Override
    protected void onStop() {
        super.onStop();

        saveState();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        setTheme(true);
    }

    private void saveState() {
        SharedPreferences settings = getSharedPreferences(prefsName, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(statePrefName, TextUtils.join("\t", commands));
        ArrayList<String> executionTimes = new ArrayList<>();
        //noinspection Convert2streamapi
        for (Long time : commandExecutionTimes) {
            executionTimes.add(time.toString());
        }
        editor.putString(datesPrefName, TextUtils.join("\t", executionTimes));
        editor.apply();
    }

    private void initInputHandling(final Story story) {
        final MessageInput input = findViewById(getCurrentThemeIndex() == 1 ? R.id.input2 : R.id.input);

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
                if (TextUtils.indexOf(inputText, '\n') < 0) return;
                String[] lines = inputText.split("\n");
                if (lines.length < 1) {
                    if (inputText.length() > 0) input.getInputEditText().setText("");
                    return;
                }
                interact(lines[0], story, new Date());
                input.getInputEditText().setText("");
            }
        });

        input.setInputListener(input1 -> {
            String interaction = input1.toString();
            interact(interaction, story, new Date());
            return true;
        });
    }

    private void interact(String interaction, Story story, Date date) {
        try {
            commands.add(interaction);
            commandExecutionTimes.add(date.getTime());
            addText(interaction, userAuthor, false, date);
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
            story = new StringLoader(new TxtParser()).load(new String(b));
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
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        if (isFinished) {
            menu.findItem(R.id.action_hint).setEnabled(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private boolean reset() {
        //messagesList.scrollTo(0, 0);
        resetViewData();
        isFinished = false;
        adapter.notifyDataSetChanged();
        story.setState("");
        try {
            story.tell();
        } catch (StoryException e) {
            System.err.println(e.getMessage());
            showError(defaultErrorMessage);
        }
        findViewById(getCurrentThemeIndex() == 1 ? R.id.input2 : R.id.input).setVisibility(View.VISIBLE);
        invalidateOptionsMenu();
        return true;
    }

    private void resetViewData() {
        commands.clear();
        commandExecutionTimes.clear();
        adapter.clear();
    }

    private void hint() {
        interact("hint", story, new Date());
    }

    @Override
    public void onReact(String text) {
        addText(text, botAuthor, shouldScroll(), restoreDate != null ? restoreDate : new Date());
    }

    @Override
    public void onFinish() {
        findViewById(getCurrentThemeIndex() == 1 ? R.id.input2 : R.id.input).setVisibility(View.GONE);
        isFinished = true;
        invalidateOptionsMenu();
        addText("The End. Thanks for playing!", botAuthor, shouldScroll(), restoreDate != null ? restoreDate : new Date());
        showStats();
    }

    private void showStats() {
        Statistics statistics = story.getStatistics();
        addText(String.format(Locale.ENGLISH, "Stats:\nEfficiency: %.2f\nHelplessness: %.2f\nClumsiness: %.2f",
                statistics.getEfficiency(),
                statistics.getHelplessness(),
                statistics.getClumsiness()),
                botAuthor, shouldScroll(),
                restoreDate != null ? restoreDate : new Date());
    }

    private boolean shouldScroll() {
        return !commands.isEmpty(); // do not scroll for initial text
    }

    private synchronized void addText(String text, Author author, Boolean scroll, Date date) {
        String message = text.replace("\\n", "\r\n");
        adapter.addToStart(new Message(UUID.randomUUID().toString(), message, author, date), false);
        if (scroll) {
            messagesList.smoothScrollBy(0, messagesList.getHeight()); // scroll to the top of the message, not the bottom as does addToStart(.., true)
        }
        if (!shouldScroll() && messagesList.getChildAt(0) != null) {
            messagesList.smoothScrollToPosition(1);
        }
    }

    private void showError(@SuppressWarnings("SameParameterValue") String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }
}
