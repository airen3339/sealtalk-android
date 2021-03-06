package cn.rongcloud.im.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.rongcloud.im.App;
import cn.rongcloud.im.R;
import cn.rongcloud.im.SealConst;
import cn.rongcloud.im.SealUserInfoManager;
import cn.rongcloud.im.db.Friend;
import cn.rongcloud.im.db.GroupMember;
import cn.rongcloud.im.server.broadcast.BroadcastManager;
import cn.rongcloud.im.server.network.http.HttpException;
import cn.rongcloud.im.server.pinyin.CharacterParser;
import cn.rongcloud.im.server.pinyin.PinyinComparator;
import cn.rongcloud.im.server.pinyin.SideBar;
import cn.rongcloud.im.server.response.AddGroupMemberResponse;
import cn.rongcloud.im.server.response.DeleteGroupMemberResponse;
import cn.rongcloud.im.server.utils.NLog;
import cn.rongcloud.im.server.utils.NToast;
import cn.rongcloud.im.server.widget.DialogWithYesOrNoUtils;
import cn.rongcloud.im.server.widget.LoadDialog;
import cn.rongcloud.im.server.widget.SelectableRoundedImageView;
import io.rong.imageloader.core.ImageLoader;
import io.rong.imkit.RongIM;
import io.rong.imkit.userInfoCache.RongUserInfoManager;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.UserInfo;

/**
 * Created by AMing on 16/1/21.
 * Company RongCloud
 */
public class SelectFriendsActivity extends BaseActivity implements View.OnClickListener {

    private static final int ADD_GROUP_MEMBER = 21;
    private static final int DELETE_GROUP_MEMBER = 23;
    public static final String DISCUSSION_UPDATE = "DISCUSSION_UPDATE";
    /**
     * ??????????????? ListView
     */
    private ListView mListView;
    /**
     * ?????????????????? adapter
     */
    private StartDiscussionAdapter adapter;
    /**
     * ???????????????????????????
     */
    public TextView dialog;

    /**
     * ???????????????????????????
     */
    private CharacterParser mCharacterParser;
    /**
     * ?????????????????????ListView??????????????????
     */
    private PinyinComparator pinyinComparator;
    private TextView mNoFriends;
    private List<Friend> data_list = new ArrayList<>();
    private List<Friend> sourceDataList = new ArrayList<>();
    private LinearLayout mSelectedFriendsLinearLayout;
    private boolean isCrateGroup;
    private boolean isConversationActivityStartDiscussion;
    private boolean isConversationActivityStartPrivate;
    private List<GroupMember> addGroupMemberList;
    private List<GroupMember> deleteGroupMemberList;
    private String groupId;
    private String conversationStartId;
    private String conversationStartType = "null";
    private ArrayList<String> discListMember;
    private ArrayList<UserInfo> addDisList, deleDisList;
    private boolean isStartPrivateChat;
    private List<Friend> mSelectedFriend;
    private boolean isAddGroupMember;
    private boolean isDeleteGroupMember;

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_disc);
        Button rightButton = getHeadRightButton();
        rightButton.setVisibility(View.GONE);
        mHeadRightText.setVisibility(View.VISIBLE);
        mHeadRightText.setText("??????");
        mHeadRightText.setOnClickListener(this);
        mSelectedFriend = new ArrayList<>();
        mSelectedFriendsLinearLayout = (LinearLayout) findViewById(R.id.ll_selected_friends);
        isCrateGroup = getIntent().getBooleanExtra("createGroup", false);
        isConversationActivityStartDiscussion = getIntent().getBooleanExtra("CONVERSATION_DISCUSSION", false);
        isConversationActivityStartPrivate = getIntent().getBooleanExtra("CONVERSATION_PRIVATE", false);
        groupId = getIntent().getStringExtra("GroupId");
        isAddGroupMember = getIntent().getBooleanExtra("isAddGroupMember", false);
        isDeleteGroupMember = getIntent().getBooleanExtra("isDeleteGroupMember", false);
        if (isAddGroupMember || isDeleteGroupMember) {
            initGroupMemberList();
        }
        addDisList = (ArrayList<UserInfo>) getIntent().getSerializableExtra("AddDiscuMember");
        deleDisList = (ArrayList<UserInfo>) getIntent().getSerializableExtra("DeleteDiscuMember");

        setTitle();
        initView();

        /**
         * ????????????????????????????????????,?????????????????????????????????????????????????????????,?????????????????????????????????
         * ????????????????????????????????????????????????,????????????????????????
         * ????????????adapter??????
         * ?????????????????????????????????????????????
         */
        initData();
    }

    private void initGroupMemberList() {
        SealUserInfoManager.getInstance().getGroupMembers(groupId, new SealUserInfoManager.ResultCallback<List<GroupMember>>() {
            @Override
            public void onSuccess(List<GroupMember> groupMembers) {
                if (isAddGroupMember) {
                    addGroupMemberList = groupMembers;
                    fillSourceDataListWithFriendsInfo();
                } else {
                    deleteGroupMemberList = groupMembers;
                    fillSourceDataListForDeleteGroupMember();
                }
            }

            @Override
            public void onError(String errString) {

            }
        });
    }

    private void setTitle() {
        if (isConversationActivityStartPrivate) {
            conversationStartType = "PRIVATE";
            conversationStartId = getIntent().getStringExtra("DEMO_FRIEND_TARGETID");
            setTitle("?????????????????????");
        } else if (isConversationActivityStartDiscussion) {
            conversationStartType = "DISCUSSION";
            conversationStartId = getIntent().getStringExtra("DEMO_FRIEND_TARGETID");
            discListMember = getIntent().getStringArrayListExtra("DISCUSSIONMEMBER");
            setTitle("?????????????????????");
        } else if (isDeleteGroupMember) {
            setTitle(getString(R.string.remove_group_member));
        } else if (isAddGroupMember) {
            setTitle(getString(R.string.add_group_member));
        } else if (isCrateGroup) {
            setTitle(getString(R.string.select_group_member));
        } else if (addDisList != null) {
            setTitle("?????????????????????");
        } else if (deleDisList != null) {
            setTitle("?????????????????????");
        } else {
            setTitle(getString(R.string.select_contact));
            if (!getSharedPreferences("config", MODE_PRIVATE).getBoolean("isDebug", false)) {
                isStartPrivateChat = true;
            }
        }
    }

    private void initView() {
        //???????????????????????????
        mCharacterParser = CharacterParser.getInstance();
        pinyinComparator = PinyinComparator.getInstance();
        mListView = (ListView) findViewById(R.id.dis_friendlistview);
        mNoFriends = (TextView) findViewById(R.id.dis_show_no_friend);
        SideBar mSidBar = (SideBar) findViewById(R.id.dis_sidrbar);
        dialog = (TextView) findViewById(R.id.dis_dialog);
        mSidBar.setTextView(dialog);
        //????????????????????????
        mSidBar.setOnTouchingLetterChangedListener(new SideBar.OnTouchingLetterChangedListener() {

            @Override
            public void onTouchingLetterChanged(String s) {
                //??????????????????????????????
                int position = adapter.getPositionForSection(s.charAt(0));
                if (position != -1) {
                    mListView.setSelection(position);
                }
            }
        });

        adapter = new StartDiscussionAdapter(mContext, sourceDataList);
        mListView.setAdapter(adapter);
    }

    private void initData() {
        if (deleDisList != null && deleDisList.size() > 0) {
            for (int i = 0; i < deleDisList.size(); i++) {
                if (deleDisList.get(i).getUserId().contains(getSharedPreferences("config", MODE_PRIVATE).getString(SealConst.SEALTALK_LOGIN_ID, ""))) {
                    continue;
                }
                data_list.add(new Friend(deleDisList.get(i).getUserId(),
                        deleDisList.get(i).getName(),
                        deleDisList.get(i).getPortraitUri(),
                        null //TODO displayName ???????????? ?????? null
                ));
            }
            /**
             * ??????3??????????????????
             * 1.????????????sourceDataList
             * 2.????????????,?????????????????????????????????????????????????????????,???????????????????????????????????????
             * 3.??????adapter??????
             */
            fillSourceDataList();
            filterSourceDataList();
            updateAdapter();
        } else if (!isDeleteGroupMember && !isAddGroupMember) {
            fillSourceDataListWithFriendsInfo();
        }
    }

    private void fillSourceDataList() {
        if (data_list != null && data_list.size() > 0) {
            sourceDataList = filledData(data_list); //?????????????????????????????????  ??????????????? ??????????????????
        } else {
            mNoFriends.setVisibility(View.VISIBLE);
        }

        //??????????????????????????????????????????
        for (int i = 0; i < data_list.size(); i++) {
            sourceDataList.get(i).setName(data_list.get(i).getName());
            sourceDataList.get(i).setUserId(data_list.get(i).getUserId());
            sourceDataList.get(i).setPortraitUri(data_list.get(i).getPortraitUri());
            sourceDataList.get(i).setDisplayName(data_list.get(i).getDisplayName());
        }
        // ??????a-z?????????????????????
        Collections.sort(sourceDataList, pinyinComparator);
    }

    //????????????????????????????????????????????????????????????????????????
    private void filterSourceDataList() {
        if (addDisList != null && addDisList.size() > 0) {
            for (UserInfo u : addDisList) {
                for (int i = 0; i < sourceDataList.size(); i++) {
                    if (sourceDataList.get(i).getUserId().contains(u.getUserId())) {
                        sourceDataList.remove(sourceDataList.get(i));
                    }
                }
            }
        } else if (addGroupMemberList != null && addGroupMemberList.size() > 0) {
            for (GroupMember addMember : addGroupMemberList) {
                for (int i = 0; i < sourceDataList.size(); i++) {
                    if (sourceDataList.get(i).getUserId().contains(addMember.getUserId())) {
                        sourceDataList.remove(sourceDataList.get(i));
                    }
                }
            }
        } else if (conversationStartType.equals("DISCUSSION")) {
            if (discListMember != null && discListMember.size() > 1) {
                for (String s : discListMember) {
                    for (int i = 0; i < sourceDataList.size(); i++) {
                        if (sourceDataList.get(i).getUserId().contains(s)) {
                            sourceDataList.remove(sourceDataList.get(i));
                        }
                    }
                }
            }
        } else if (conversationStartType.equals("PRIVATE")) {
            for (int i = 0; i < sourceDataList.size(); i++) {
                if (sourceDataList.get(i).getUserId().contains(conversationStartId)) {
                    sourceDataList.remove(sourceDataList.get(i));
                }
            }
        }
    }

    private void updateAdapter() {
        adapter.setData(sourceDataList);
        adapter.notifyDataSetChanged();
    }

    private void fillSourceDataListWithFriendsInfo() {
        SealUserInfoManager.getInstance().getFriends(new SealUserInfoManager.ResultCallback<List<Friend>>() {
            @Override
            public void onSuccess(List<Friend> friendList) {
                if (mListView != null) {
                    if (friendList != null && friendList.size() > 0) {
                        for (Friend friend : friendList) {
                            data_list.add(new Friend(friend.getUserId(), friend.getName(), friend.getPortraitUri(), friend.getDisplayName(), null, null));
                        }
                        if (isAddGroupMember) {
                            for (GroupMember groupMember : addGroupMemberList) {
                                for (int i = 0; i < data_list.size(); i++) {
                                    if (groupMember.getUserId().equals(data_list.get(i).getUserId())) {
                                        data_list.remove(i);
                                    }
                                }
                            }
                        }
                        fillSourceDataList();
                        filterSourceDataList();
                        updateAdapter();
                    }
                }
            }

            @Override
            public void onError(String errString) {

            }
        });
    }

    private void fillSourceDataListForDeleteGroupMember() {
        if (deleteGroupMemberList != null && deleteGroupMemberList.size() > 0) {
            for (GroupMember deleteMember : deleteGroupMemberList) {
                if (deleteMember.getUserId().contains(getSharedPreferences("config", MODE_PRIVATE).getString(SealConst.SEALTALK_LOGIN_ID, ""))) {
                    continue;
                }
                data_list.add(new Friend(deleteMember.getUserId(),
                        deleteMember.getName(), deleteMember.getPortraitUri(),
                        null //TODO displayName ???????????? ?????? null
                ));
            }
            fillSourceDataList();
            updateAdapter();
        }
    }


    //????????????CheckBox????????????
    public Map<Integer, Boolean> mCBFlag;

    public List<Friend> adapterList;


    class StartDiscussionAdapter extends BaseAdapter implements SectionIndexer {

        private Context context;
        private ArrayList<CheckBox> checkBoxList = new ArrayList<>();

        public StartDiscussionAdapter(Context context, List<Friend> list) {
            this.context = context;
            adapterList = list;
            mCBFlag = new HashMap<>();
            init();
        }

        public void setData(List<Friend> friends) {
            adapterList = friends;
            init();
        }

        void init() {
            for (int i = 0; i < adapterList.size(); i++) {
                mCBFlag.put(i, false);
            }
        }

        /**
         * ?????????????????? ??????UI?????????
         */
        public void updateListView(List<Friend> list) {
            adapterList = list;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return adapterList.size();
        }

        @Override
        public Object getItem(int position) {
            return adapterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {

            final ViewHolder viewHolder;
            final Friend friend = adapterList.get(position);
            if (convertView == null) {
                viewHolder = new ViewHolder();
                convertView = LayoutInflater.from(context).inflate(R.layout.item_start_discussion, parent, false);
                viewHolder.tvTitle = (TextView) convertView.findViewById(R.id.dis_friendname);
                viewHolder.tvLetter = (TextView) convertView.findViewById(R.id.dis_catalog);
                viewHolder.mImageView = (SelectableRoundedImageView) convertView.findViewById(R.id.dis_frienduri);
                viewHolder.isSelect = (CheckBox) convertView.findViewById(R.id.dis_select);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            //??????position???????????????????????????Char ascii???
            int section = getSectionForPosition(position);
            //?????????????????????????????????????????????Char????????? ??????????????????????????????
            if (position == getPositionForSection(section)) {
                viewHolder.tvLetter.setVisibility(View.VISIBLE);
                viewHolder.tvLetter.setText(friend.getLetters());
            } else {
                viewHolder.tvLetter.setVisibility(View.GONE);
            }

            if (isStartPrivateChat) {
                viewHolder.isSelect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CheckBox cb = (CheckBox) v;
                        if (cb != null) {
                            if (cb.isChecked()) {
                                for (CheckBox c : checkBoxList) {
                                    c.setChecked(false);
                                }
                                checkBoxList.clear();
                                checkBoxList.add(cb);
                            } else {
                                checkBoxList.clear();
                            }
                        }
                    }
                });
                viewHolder.isSelect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        mCBFlag.put(position, viewHolder.isSelect.isChecked());
                    }
                });
            } else {
                viewHolder.isSelect.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mCBFlag.put(position, viewHolder.isSelect.isChecked());
                        updateSelectedSizeView(mCBFlag);
                        if (mSelectedFriend.contains(friend)) {
                            int index = mSelectedFriend.indexOf(friend);
                            if (index > -1) {
                                mSelectedFriendsLinearLayout.removeViewAt(index);
                            }
                            mSelectedFriend.remove(friend);
                        } else {
                            mSelectedFriend.add(friend);
                            LinearLayout view = (LinearLayout) View.inflate(SelectFriendsActivity.this, R.layout.item_selected_friends, null);
                            SelectableRoundedImageView asyncImageView = (SelectableRoundedImageView) view.findViewById(R.id.iv_selected_friends);
                            String portraitUri = SealUserInfoManager.getInstance().getPortraitUri(friend);
                            ImageLoader.getInstance().displayImage(portraitUri, asyncImageView);
                            view.removeView(asyncImageView);
                            mSelectedFriendsLinearLayout.addView(asyncImageView);
                        }
                    }
                });
            }
            viewHolder.isSelect.setChecked(mCBFlag.get(position));

            if (TextUtils.isEmpty(adapterList.get(position).getDisplayName())) {
                viewHolder.tvTitle.setText(adapterList.get(position).getName());
            } else {
                viewHolder.tvTitle.setText(adapterList.get(position).getDisplayName());
            }

            String portraitUri = SealUserInfoManager.getInstance().getPortraitUri(adapterList.get(position));
            ImageLoader.getInstance().displayImage(portraitUri, viewHolder.mImageView, App.getOptions());
            return convertView;
        }

        private void updateSelectedSizeView(Map<Integer, Boolean> mCBFlag) {
            if (!isStartPrivateChat && mCBFlag != null) {
                int size = 0;
                for (int i = 0; i < mCBFlag.size(); i++) {
                    if (mCBFlag.get(i)) {
                        size++;
                    }
                }
                if (size == 0) {
                    mHeadRightText.setText("??????");
                    mSelectedFriendsLinearLayout.setVisibility(View.GONE);
                } else {
                    mHeadRightText.setText("??????(" + size + ")");
                    List<Friend> selectedList = new ArrayList<>();
                    for (int i = 0; i < sourceDataList.size(); i++) {
                        if (mCBFlag.get(i)) {
                            selectedList.add(sourceDataList.get(i));
                        }
                    }
                    mSelectedFriendsLinearLayout.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public Object[] getSections() {
            return new Object[0];
        }

        /**
         * ???????????????????????????Char ascii????????????????????????????????????????????????
         */
        @Override
        public int getPositionForSection(int sectionIndex) {
            for (int i = 0; i < getCount(); i++) {
                String sortStr = adapterList.get(i).getLetters();
                char firstChar = sortStr.toUpperCase().charAt(0);
                if (firstChar == sectionIndex) {
                    return i;
                }
            }

            return -1;
        }

        /**
         * ??????ListView??????????????????????????????????????????Char ascii???
         */
        @Override
        public int getSectionForPosition(int position) {
            return adapterList.get(position).getLetters().charAt(0);
        }


        final class ViewHolder {
            /**
             * ?????????
             */
            TextView tvLetter;
            /**
             * ??????
             */
            TextView tvTitle;
            /**
             * ??????
             */
            SelectableRoundedImageView mImageView;
            /**
             * userid
             */
//            TextView tvUserId;
            /**
             * ??????????????????checkbox
             */
            CheckBox isSelect;
        }

    }


    @Override
    public Object doInBackground(int requestCode, String id) throws HttpException {
        switch (requestCode) {
            case ADD_GROUP_MEMBER:
                return action.addGroupMember(groupId, startDisList);
            case DELETE_GROUP_MEMBER:
                return action.deleGroupMember(groupId, startDisList);
        }
        return super.doInBackground(requestCode, id);
    }

    @Override
    public void onSuccess(int requestCode, Object result) {
        if (result != null) {
            switch (requestCode) {
                case ADD_GROUP_MEMBER:
                    AddGroupMemberResponse res = (AddGroupMemberResponse) result;
                    if (res.getCode() == 200) {
                        Intent data = new Intent();
                        data.putExtra("newAddMember", (Serializable) createGroupList);
                        setResult(101, data);
                        LoadDialog.dismiss(mContext);
                        NToast.shortToast(mContext, getString(R.string.add_successful));
                        finish();
                    }
                    break;
                case DELETE_GROUP_MEMBER:
                    DeleteGroupMemberResponse response = (DeleteGroupMemberResponse) result;
                    if (response.getCode() == 200) {
                        Intent intent = new Intent();
                        intent.putExtra("deleteMember", (Serializable) createGroupList);
                        setResult(102, intent);
                        LoadDialog.dismiss(mContext);
                        NToast.shortToast(mContext, getString(R.string.remove_successful));
                        finish();
                    } else if (response.getCode() == 400) {
                        LoadDialog.dismiss(mContext);
                        NToast.shortToast(mContext, "??????????????????????????????");
                    }
                    break;
            }
        }
    }

    @Override
    public void onFailure(int requestCode, int state, Object result) {
        switch (requestCode) {
            case ADD_GROUP_MEMBER:
                LoadDialog.dismiss(mContext);
                NToast.shortToast(mContext, "??????????????????????????????");
                break;
            case DELETE_GROUP_MEMBER:
                LoadDialog.dismiss(mContext);
                NToast.shortToast(mContext, "??????????????????????????????");
                break;
        }
    }

    private List<String> startDisList;
    private List<Friend> createGroupList;


    /**
     * ???ListView????????????
     */
    private List<Friend> filledData(List<Friend> list) {
        List<Friend> mFriendList = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            Friend friendModel = new Friend(list.get(i).getUserId(), list.get(i).getName(), list.get(i).getPortraitUri());
            //?????????????????????
            String pinyin = null;
            if (!TextUtils.isEmpty(list.get(i).getDisplayName())) {
                pinyin = mCharacterParser.getSpelling(list.get(i).getDisplayName());
            } else if (!TextUtils.isEmpty(list.get(i).getName())) {
                pinyin = mCharacterParser.getSpelling(list.get(i).getName());
            } else {
                UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(list.get(i).getUserId());
                if (userInfo != null) {
                    pinyin = mCharacterParser.getSpelling(userInfo.getName());
                }
            }
            String sortString;
            if (!TextUtils.isEmpty(pinyin)) {
                sortString = pinyin.substring(0, 1).toUpperCase();
            } else {
                sortString = "#";
            }

            // ??????????????????????????????????????????????????????
            if (sortString.matches("[A-Z]")) {
                friendModel.setLetters(sortString);
            } else {
                friendModel.setLetters("#");
            }

            mFriendList.add(friendModel);
        }
        return mFriendList;

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mListView = null;
        adapter = null;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.text_right:
                if (mCBFlag != null && sourceDataList != null && sourceDataList.size() > 0) {
                    startDisList = new ArrayList<>();
                    List<String> disNameList = new ArrayList<>();
                    createGroupList = new ArrayList<>();
                    for (int i = 0; i < sourceDataList.size(); i++) {
                        if (mCBFlag.get(i)) {
                            startDisList.add(sourceDataList.get(i).getUserId());
                            disNameList.add(sourceDataList.get(i).getName());
                            createGroupList.add(sourceDataList.get(i));
                        }
                    }

                    if (isConversationActivityStartDiscussion) {
                        if (RongIM.getInstance() != null) {
                            RongIM.getInstance().addMemberToDiscussion(conversationStartId, startDisList, new RongIMClient.OperationCallback() {
                                @Override
                                public void onSuccess() {
                                    NToast.shortToast(SelectFriendsActivity.this, getString(R.string.add_successful));
                                    BroadcastManager.getInstance(mContext).sendBroadcast(DISCUSSION_UPDATE);
                                    finish();
                                }

                                @Override
                                public void onError(RongIMClient.ErrorCode errorCode) {

                                }
                            });
                        }
                    } else if (isConversationActivityStartPrivate) {
                        if (RongIM.getInstance() != null) { // ??????????????? ?????????????????????
                            RongIM.getInstance().addMemberToDiscussion(conversationStartId, startDisList, new RongIMClient.OperationCallback() {
                                @Override
                                public void onSuccess() {
                                    NToast.shortToast(SelectFriendsActivity.this, getString(R.string.add_successful));
                                    finish();
                                }

                                @Override
                                public void onError(RongIMClient.ErrorCode errorCode) {

                                }
                            });
                        }
                    } else if (deleteGroupMemberList != null && startDisList != null && sourceDataList.size() > 0) {
                        mHeadRightText.setClickable(true);
                        DialogWithYesOrNoUtils.getInstance().showDialog(mContext, getString(R.string.remove_group_members), new DialogWithYesOrNoUtils.DialogCallBack() {

                            @Override
                            public void executeEvent() {
                                LoadDialog.show(mContext);
                                request(DELETE_GROUP_MEMBER);
                            }

                            @Override
                            public void executeEditEvent(String editText) {

                            }

                            @Override
                            public void updatePassword(String oldPassword, String newPassword) {

                            }
                        });
                    } else if (deleDisList != null && startDisList != null && startDisList.size() > 0) {
                        Intent intent = new Intent();
                        intent.putExtra("deleteDiscuMember", (Serializable) startDisList);
                        setResult(RESULT_OK, intent);
                        finish();

                    } else if (addGroupMemberList != null && startDisList != null && startDisList.size() > 0) {
                        //TODO ??????????????????????????????????????????????????????  ???????????????????????????
                        LoadDialog.show(mContext);
                        request(ADD_GROUP_MEMBER);

                    } else if (addDisList != null && startDisList != null && startDisList.size() > 0) {
                        Intent intent = new Intent();
                        intent.putExtra("addDiscuMember", (Serializable) startDisList);
                        setResult(RESULT_OK, intent);
                        finish();
                    } else if (isCrateGroup) {
                        if (createGroupList.size() > 0) {
                            mHeadRightText.setClickable(true);
                            Intent intent = new Intent(SelectFriendsActivity.this, CreateGroupActivity.class);
                            intent.putExtra("GroupMember", (Serializable) createGroupList);
                            startActivity(intent);
                            finish();
                        } else {
                            NToast.shortToast(mContext, "???????????????????????????????????????");
                            mHeadRightText.setClickable(true);
                        }
                    } else {

                        if (startDisList != null && startDisList.size() == 1) {
                            RongIM.getInstance().startPrivateChat(mContext, startDisList.get(0),
                                    SealUserInfoManager.getInstance().getFriendByID(startDisList.get(0)).getName());
                        } else if (startDisList.size() > 1) {

                            String disName;
                            if (disNameList.size() < 2) {
                                disName = disNameList.get(0) + "??????????????????";
                            } else {
                                StringBuilder sb = new StringBuilder();
                                for (String s : disNameList) {
                                    sb.append(s);
                                    sb.append(",");
                                }
                                String str = sb.toString();
                                disName = str.substring(0, str.length() - 1);
                                disName = disName + "??????????????????";
                            }
                            RongIM.getInstance().createDiscussion(disName, startDisList, new RongIMClient.CreateDiscussionCallback() {
                                @Override
                                public void onSuccess(String s) {
                                    NLog.e("disc", "onSuccess" + s);
                                    RongIM.getInstance().startDiscussionChat(SelectFriendsActivity.this, s, "");
                                }

                                @Override
                                public void onError(RongIMClient.ErrorCode errorCode) {
                                    NLog.e("disc", errorCode.getValue());
                                }
                            });
                        } else {
                            mHeadRightText.setClickable(true);
                            NToast.shortToast(mContext, getString(R.string.least_one_friend));
                        }
                    }
                } else {
                    Toast.makeText(SelectFriendsActivity.this, "?????????", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
}
