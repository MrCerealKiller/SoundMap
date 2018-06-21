package ca.mcgill.cim.soundmap;

import android.app.Dialog;
import android.content.Intent;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.ArrayList;
import java.util.List;

public class LandingPageActivity extends AppCompatActivity {

    private static final String TAG = "LandingPage";

    private static final int ERROR_DIALOG_REQUEST = 9001;

    private String mUser;

    private TabLayout mTabLayout;
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing_page);

        // Check to ensure Google Play Services is active and up-to-date
        if (isServicesAvailable()) {
            Log.d(TAG, "onCreate: Google Play Services are available");
        } else {
            Log.w(TAG, "onCreate: Google Play Services are not available");
            // TODO : Disable button
        }

//        mViewPager = (ViewPager) findViewById(R.id.viewpager);
//        setupViewPager(mViewPager);
//
//        mTabLayout = (TabLayout) findViewById(R.id.tabs);
//        mTabLayout.setupWithViewPager(mViewPager);

        if (mUser == null || mUser.trim() == "") {
            attemptSignIn();
        }
    }

    // Check that the Google Play Services is available and compatible
    private boolean isServicesAvailable() {
        Log.d(TAG, "isServicesAvailable: Verifying version and connectivity");
        int available = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(LandingPageActivity.this);

        if (available == ConnectionResult.SUCCESS) {
            Log.d(TAG, "isServicesAvailable: Google Play Services successfully connected.");
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(available)) {
            Log.d(TAG, "isServicesAvailable: An error occurred, but can be resolved by the user");
            Dialog dialog = GoogleApiAvailability.getInstance()
                    .getErrorDialog(LandingPageActivity.this, available, ERROR_DIALOG_REQUEST);
            dialog.show();
            return false;
        } else {
            Log.d(TAG, "isServicesAvailable: An unresolvable error occurred");
            Toast.makeText(this, "An unresolvable error occurred with Google Play Services",
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void attemptSignIn() {
        Intent signInScreenIntent = new Intent(this, LoginActivity.class);
        final int result = 1;
        startActivityForResult(signInScreenIntent, result);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 1 && resultCode == RESULT_OK) {
            Bundle signInResult = data.getExtras();
            if (signInResult != null) {
                mUser = signInResult.getString("email");
            }
        }
    }

    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new InstructionsFragment(), "Instructions");
        adapter.addFragment(new TermsFragment(), "Terms");
        viewPager.setAdapter(adapter);
    }

    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }

    @Override
    public void onBackPressed() {
        // Do nothing...
    }
}
