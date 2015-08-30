package com.habitrpg.android.habitica;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.github.florent37.materialviewpager.MaterialViewPager;
import com.habitrpg.android.habitica.callbacks.HabitRPGUserCallback;
import com.habitrpg.android.habitica.callbacks.TaskCreationCallback;
import com.habitrpg.android.habitica.callbacks.TaskScoringCallback;
import com.habitrpg.android.habitica.callbacks.TaskUpdateCallback;
import com.habitrpg.android.habitica.events.AddTaskTappedEvent;
import com.habitrpg.android.habitica.events.BuyRewardTappedEvent;
import com.habitrpg.android.habitica.events.HabitScoreEvent;
import com.habitrpg.android.habitica.events.TaskLongPressedEvent;
import com.habitrpg.android.habitica.events.TaskSaveEvent;
import com.habitrpg.android.habitica.events.TaskTappedEvent;
import com.habitrpg.android.habitica.events.TodoCheckedEvent;
import com.habitrpg.android.habitica.events.ToggledInnStateEvent;
import com.habitrpg.android.habitica.prefs.PrefsActivity;
import com.habitrpg.android.habitica.ui.AvatarWithBarsViewModel;
import com.habitrpg.android.habitica.ui.EditTextDrawer;
import com.habitrpg.android.habitica.ui.MainDrawerBuilder;
import com.habitrpg.android.habitica.ui.adapter.HabitItemRecyclerViewAdapter;
import com.habitrpg.android.habitica.ui.fragments.TaskRecyclerViewFragment;
import com.instabug.wrapper.support.activity.InstabugAppCompatActivity;
import com.magicmicky.habitrpgwrapper.lib.models.ContentResult;
import com.magicmicky.habitrpgwrapper.lib.models.HabitRPGUser;
import com.magicmicky.habitrpgwrapper.lib.models.Tag;
import com.magicmicky.habitrpgwrapper.lib.models.TaskDirection;
import com.magicmicky.habitrpgwrapper.lib.models.TaskDirectionData;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.ItemData;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.Task;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.SectionDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.raizlabs.android.dbflow.runtime.FlowContentObserver;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.Select;
import com.raizlabs.android.dbflow.structure.BaseModel;
import com.raizlabs.android.dbflow.structure.Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.greenrobot.event.EventBus;
import io.fabric.sdk.android.Fabric;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MainActivity extends InstabugAppCompatActivity implements HabitRPGUserCallback.OnUserReceived,
        TaskScoringCallback.OnTaskScored, OnTaskCreationListener,
        FlowContentObserver.OnSpecificModelStateChangedListener, TaskCreationCallback.OnHabitCreated, TaskUpdateCallback.OnHabitUpdated,
        Callback<List<ItemData>> {

    static final int TASK_CREATED_RESULT = 1;
    static final int TASK_UPDATED_RESULT = 2;

    //region View Elements
    @InjectView(R.id.materialViewPager)
    MaterialViewPager materialViewPager;

    Toolbar toolbar;
    Drawer drawer;

    Drawer filterDrawer;
    //endregion

    Map<Integer, TaskRecyclerViewFragment> ViewFragmentsDictionary = new HashMap<Integer, TaskRecyclerViewFragment>();

    List<Task> TaskList = new ArrayList<Task>();

    private HostConfig hostConfig;
    APIHelper mAPIHelper;

    android.support.v4.view.ViewPager viewPager;

    // just to test the view
    private HabitRPGUser User;

    AvatarWithBarsViewModel avatarInHeader;

    FlowContentObserver observer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inject Controls
        ButterKnife.inject(this);

        // Receive Events
        EventBus.getDefault().register(this);

        // Initialize Crashlytics
        Crashlytics crashlytics = new Crashlytics.Builder()
                .core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())
                .build();
        Fabric.with(this, crashlytics);

        this.hostConfig = PrefsActivity.fromContext(this);
        if (hostConfig == null || hostConfig.getApi() == null || hostConfig.getApi().equals("") || hostConfig.getUser() == null || hostConfig.getUser().equals("")) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        toolbar = materialViewPager.getToolbar();

        if (toolbar != null) {
            setSupportActionBar(toolbar);

            ActionBar actionBar = getSupportActionBar();

            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(false);
                actionBar.setDisplayShowTitleEnabled(true);
                actionBar.setDisplayUseLogoEnabled(false);
                actionBar.setHomeButtonEnabled(false);
            }

            toolbar.setPadding(0, getResources().getDimensionPixelSize(R.dimen.tool_bar_top_padding), 0, 0);
        }

        materialViewPager.setBackgroundColor(getResources().getColor(R.color.white));

        View mPagerRootView = materialViewPager.getRootView();

        View avatarHeaderView = mPagerRootView.findViewById(R.id.avatar_with_bars_layout);

        avatarInHeader = new AvatarWithBarsViewModel(this, avatarHeaderView);

        drawer = MainDrawerBuilder.CreateDefaultBuilderSettings(this, toolbar)
                .withSelectedItem(0)
                .build();

        final android.content.Context context = getApplicationContext();

        filterDrawer = new DrawerBuilder()
                .withActivity(this)
                .withOnDrawerItemLongClickListener(new Drawer.OnDrawerItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l, IDrawerItem iDrawerItem) {
                        Toast toast = Toast.makeText(context, "Long Pressed", Toast.LENGTH_LONG);

                        toast.show();

                        return true;
                    }
                })
                .withDrawerGravity(Gravity.RIGHT)
                .append(drawer);


        viewPager = materialViewPager.getViewPager();
        viewPager.setOffscreenPageLimit(6);

        materialViewPager.getViewPager().setCurrentItem(0);

        User = new Select().from(HabitRPGUser.class).where(Condition.column("id").eq(hostConfig.getUser())).querySingle();
        this.observer = new FlowContentObserver();
        this.observer.registerForContentChanges(this.getApplicationContext(), HabitRPGUser.class);

        this.observer.addSpecificModelChangeListener(this);

        try {
            hasItemData = new Select().from(ItemData.class).querySingle() != null;
        } catch (Exception e) {

        }
        SetUserData(false);
    }

    private boolean hasItemData = false;

    @Override
    protected void onResume() {
        super.onResume();

        if(mAPIHelper == null)
        {
            this.mAPIHelper = new APIHelper(this, hostConfig);

            mAPIHelper.retrieveUser(new HabitRPGUserCallback(this));
        }
    }

    @Override
    protected void onDestroy() {
        if (observer != null) {
            this.observer.unregisterForContentChanges(this.getApplicationContext());
        }
        EventBus.getDefault().unregister(this);

        super.onDestroy();
    }

    private void showSnackbar(String content) {
        showSnackbar(content, false);
    }

    private void showSnackbar(String content, boolean negative) {
        Fragment f = ViewFragmentsDictionary.get(materialViewPager.getViewPager().getCurrentItem());

        Snackbar snackbar = Snackbar.make(f.getView().findViewById(R.id.fab), content, Snackbar.LENGTH_LONG);

        if (negative) {
            View snackbarView = snackbar.getView();

            //change Snackbar's background color;
            snackbarView.setBackgroundColor(getResources().getColor(R.color.red));
        }

        snackbar.show();
    }

    public void onEvent(TaskTappedEvent event) {
        if(event.Task.type.equals("reward"))
            return;

        Bundle bundle = new Bundle();
        bundle.putString("type", event.Task.getType().toString());
        bundle.putString("taskId", event.Task.getId());

        Intent intent = new Intent(this, TaskFormActivity.class);
        intent.putExtras(bundle);
        startActivityForResult(intent, TASK_UPDATED_RESULT);
    }

    public void onEvent(TaskLongPressedEvent event) {
        showSnackbar("LongPress: " + event.Task.text);
    }

    public void onEvent(TodoCheckedEvent event) {
        showSnackbar("ToDo Checked= " + event.ToDo.getText(), true);
    }

    public void onEvent(HabitScoreEvent event) {
        mAPIHelper.updateTaskDirection(event.Habit.getId(), event.Up ? TaskDirection.up : TaskDirection.down, new TaskScoringCallback(this));
    }

    public void onEvent(AddTaskTappedEvent event) {
        Bundle bundle = new Bundle();
        bundle.putString("type", event.ClassType.getSimpleName().toLowerCase());

        Intent intent = new Intent(this, TaskFormActivity.class);
        intent.putExtras(bundle);
        startActivityForResult(intent, TASK_CREATED_RESULT);
    }

    public void onEvent(final BuyRewardTappedEvent event) {
        final String rewardKey = event.Reward.getId();

        if (User.getStats().getGp() < event.Reward.getValue()) {
            showSnackbar("Not enough Gold", true);
            return;
        }

        mAPIHelper.updateTaskDirection(rewardKey, TaskDirection.down, new TaskScoringCallback(this));

        /*
        if (event.Reward instanceof RewardItem) {
            if (rewardKey.equals("potion")) {
                int currentHp = User.getStats().getHp().intValue();
                int maxHp = User.getStats().getMaxHealth();

                if(currentHp == maxHp) {
                    showSnackbar("You don't need to buy an health potion", true);
                    return;
                }
            }

            mAPIHelper.apiService.buyItem(event.Reward.getId(), new Callback<Void>() {
                @Override
                public void success(Void aVoid, Response response) {

                    switch (rewardKey) {
                        case "potion":
                            double newHp = Math.min(User.getStats().getMaxHealth(), User.getStats().getHp() + 15);
                            User.getStats().setHp(newHp);

                            updateHeader();

                            break;
                    }

                    showSnackbar("Buy Reward Successful " + event.Reward.getText());
                }

                @Override
                public void failure(RetrofitError error) {

                    showSnackbar("Buy Reward Error " + event.Reward.getText(), true);
                }
            });
        } else {
            // User created Rewards
        }*/
    }

    public void onEvent(final TaskSaveEvent event) {
        Task task = (Task) event.task;
        if (event.created) {
            this.mAPIHelper.createNewTask(task, new TaskCreationCallback(this));
        } else {
            this.mAPIHelper.updateTask(task, new TaskUpdateCallback(this));
        }
    }

    public void onEvent(ToggledInnStateEvent event) {
        User.getPreferences().setSleep(event.Inn);

        updateUserAvatars();
    }

    private void notifyUser(double xp, double hp, double gold,
                            double lvl, double delta) {
        StringBuilder message = new StringBuilder();
        boolean neg = false;
        if (lvl > User.getStats().getLvl()) {
            message.append(getString(R.string.lvlup));
            //If user lvl up, we need to fetch again the data from the server...
            this.mAPIHelper.retrieveUser(new HabitRPGUserCallback(this));
            User.getStats().setLvl((int) lvl);
            showSnackbar(message.toString());
        } else {
            com.magicmicky.habitrpgwrapper.lib.models.Stats stats = User.getStats();

            if (xp > stats.getExp()) {
                message.append(" + ").append(round(xp - stats.getExp(), 2)).append(" XP");
                User.getStats().setExp(xp);
            }
            if (hp != stats.getHp()) {
                neg = true;
                message.append(" - ").append(round(stats.getHp() - hp, 2)).append(" HP");
                User.getStats().setHp(hp);
            }
            if (gold > stats.getGp()) {
                message.append(" + ").append(round(gold - stats.getGp(), 2)).append(" GP");
                stats.setGp(gold);
            } else if (gold < stats.getGp()) {
                neg = true;
                message.append(" - ").append(round(stats.getGp() - gold, 2)).append(" GP");
                stats.setGp(gold);
            }
            showSnackbar(message.toString(), neg);

            updateUserAvatars();
        }
    }

    static public Double round(Double value, int n) {
        double r = (Math.round(value.doubleValue() * Math.pow(10, n))) / (Math.pow(10, n));
        return Double.valueOf(r);

    }

    public void loadTaskLists() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();

        viewPager.setAdapter(new FragmentPagerAdapter(fragmentManager) {

            int oldPosition = -1;

            @Override
            public Fragment getItem(int position) {
                int layoutOfType;
                TaskRecyclerViewFragment fragment;

                String fragmentkey = "Recycler$" + position;
                final android.content.Context context = getApplicationContext();

                switch (position) {
                    case 0:
                        layoutOfType = R.layout.habit_item_card;
                        fragment = TaskRecyclerViewFragment.newInstance(new HabitItemRecyclerViewAdapter("habit", Task.class, layoutOfType, HabitItemRecyclerViewAdapter.HabitViewHolder.class, context), Task.class);

                        break;
                    case 1:
                        layoutOfType = R.layout.daily_item_card;
                        fragment = TaskRecyclerViewFragment.newInstance(new HabitItemRecyclerViewAdapter("daily", Task.class, layoutOfType, HabitItemRecyclerViewAdapter.DailyViewHolder.class, context), Task.class);
                        break;
                    case 3:
                        layoutOfType = R.layout.reward_item_card;
                        fragment = TaskRecyclerViewFragment.newInstance(new HabitItemRecyclerViewAdapter("reward", Task.class, layoutOfType, HabitItemRecyclerViewAdapter.RewardViewHolder.class, context), Task.class);
                        break;
                    default:
                        layoutOfType = R.layout.todo_item_card;
                        fragment = TaskRecyclerViewFragment.newInstance(new HabitItemRecyclerViewAdapter("todo", Task.class, layoutOfType, HabitItemRecyclerViewAdapter.TodoViewHolder.class, context), Task.class);
                }

                ViewFragmentsDictionary.put(position, fragment);

                return fragment;
            }

            @Override
            public int getCount() {
                return 4;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                switch (position) {
                    case 0:
                        return "Habits";
                    case 1:
                        return "Dailies";
                    case 2:
                        return "Todos";
                    case 3:
                        return "Rewards";
                }
                return "";
            }
        });

        materialViewPager.getPagerTitleStrip().setViewPager(viewPager);
    }

    public void FillTagFilterDrawer() {
        filterDrawer.removeAllItems();
        filterDrawer.addItems(
                new SectionDrawerItem().withName("Filter by Tag"),
                new EditTextDrawer()
        );

        for (Tag t : User.getTags()) {
            filterDrawer.addItem(
                    new PrimaryDrawerItem().withName(t.getName()).withBadge("" + t.getTasks().size())
            );
        }
    }

    public int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_activity_new, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_search:
                filterDrawer.openDrawer();

                return true;

        }

        return super.onOptionsItemSelected(item);
    }

    private void updateUserAvatars() {
        avatarInHeader.UpdateData(User);
    }

    private void updateHeader() {
        updateUserAvatars();
        toolbar.setTitle(User.getProfile().getName() + " - Lv" + User.getStats().getLvl());

        android.support.v7.app.ActionBarDrawerToggle actionBarDrawerToggle = drawer.getActionBarDrawerToggle();

        if (actionBarDrawerToggle != null) {
            actionBarDrawerToggle.setDrawerIndicatorEnabled(true);
        }
    }

    @Override
    public void onUserReceived(HabitRPGUser user) {
    }

    @Override
    public void onUserFail() {

    }


    @Override
    public void success(List<ItemData> items, Response response) {

        // TODO Order Rewards
        // TODO add Gear Images

        Condition.In keyCondition = Condition.column("key").in("potion");

        for (ItemData item : items) {
            keyCondition = keyCondition.and(item.key);
        }

/*
        ConditionQueryBuilder<ItemData> queryBuilder = new ConditionQueryBuilder<ItemData>(ItemData.class,
                keyCondition);

        List<ItemData> itemsFromDb = new Select().from(ItemData.class).where(queryBuilder).queryList();

        ArrayList<RewardItem> rewardList = new ArrayList<>();

        for (ItemData item : itemsFromDb) {
            RewardItem reward = new RewardItem();
            reward.text = item.text;
            reward.notes = item.notes;
            reward.value = item.value;
            reward.setId(item.key);

            rewardList.add(reward);
        }

        GearRewards.clear();
        GearRewards.addAll(rewardList);
        */
    }

    @Override
    public void failure(RetrofitError error) {

    }

    @Override
    public void onTaskDataReceived(TaskDirectionData data) {
        notifyUser(data.getExp(), data.getHp(), data.getGp(), data.getLvl(), data.getDelta());
    }

    @Override
    public void onTaskScoringFailed() {

    }

    @Override
    public void onModelStateChanged(Class<? extends Model> aClass, BaseModel.Action action, String s, String s1) {
        User = new Select().from(HabitRPGUser.class).where(Condition.column("id").eq(hostConfig.getUser())).querySingle();

        SetUserData(!taskListAlreadyAdded);
    }

    private boolean taskListAlreadyAdded;

    private boolean getContentCalled = false;

    private void SetUserData(final boolean onlyHeader) {
        if (User != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!onlyHeader) {
                        taskListAlreadyAdded = true;
                        loadTaskLists();
                        FillTagFilterDrawer();
                    }
                    updateHeader();
                }
            });


            if (mAPIHelper != null && !getContentCalled && !hasItemData) {
                getContentCalled = true;
                mAPIHelper.apiService.getContent(new Callback<ContentResult>() {
                    @Override
                    public void success(ContentResult contentResult, Response response) {
                        ArrayList<ItemData> list = new ArrayList<>();
                        list.add(contentResult.potion);
                        list.add(contentResult.armoire);
                        list.addAll(contentResult.gear.flat.values());

                        for (ItemData itemData : list) {
                            itemData.save();
                        }

                        hasItemData = true;

                        mAPIHelper.apiService.getInventoryBuyableGear(MainActivity.this);
                    }

                    @Override
                    public void failure(RetrofitError error) {

                    }
                });


            }

            if (mAPIHelper != null && hasItemData) {
                mAPIHelper.apiService.getInventoryBuyableGear(this);
            }
        }
    }

    @Override
    public void onTaskCreation(Task task, boolean editMode) {
        if (!editMode) {
            this.mAPIHelper.createNewTask(task, new TaskCreationCallback(this));
        } else {
            this.mAPIHelper.updateTask(task, new TaskUpdateCallback(this));
        }

        // TODO update task in list
    }

    @Override
    public void onTaskCreationFail(String message) {
        showSnackbar(message, true);
    }

    // TaskCreationCallback
    @Override
    public void onTaskCreated(Task habit) {
        habit.save();
    }

    @Override
    public void onTaskCreationFail() {

    }

    // TaskUpdateCallback
    @Override
    public void onTaskUpdated(Task habit) {
        habit.save();
    }

    @Override
    public void onTaskUpdateFail() {

    }
}