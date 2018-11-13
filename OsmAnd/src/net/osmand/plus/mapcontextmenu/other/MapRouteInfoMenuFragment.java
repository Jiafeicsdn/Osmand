package net.osmand.plus.mapcontextmenu.other;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.osmand.AndroidUtils;
import net.osmand.plus.LockableScrollView;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.mapcontextmenu.InterceptorLinearLayout;
import net.osmand.plus.mapcontextmenu.MenuController;
import net.osmand.plus.views.controls.HorizontalSwipeConfirm;

import static net.osmand.plus.mapcontextmenu.MapContextMenuFragment.CURRENT_Y_UNDEFINED;
import static net.osmand.plus.mapcontextmenu.MenuBuilder.SHADOW_HEIGHT_TOP_DP;

public class MapRouteInfoMenuFragment extends BaseOsmAndFragment {
	public static final String TAG = "MapRouteInfoMenuFragment";

	private MapRouteInfoMenu menu;
	private InterceptorLinearLayout mainView;
	private View toolbarContainer;
	private View view;
	private View.OnLayoutChangeListener containerLayoutListener;

	private boolean portrait;
	private boolean nightMode;
	private boolean moving;
	private boolean forceUpdateLayout;
	private boolean initLayout = true;

	private int menuFullHeight;
	private int minHalfY;
	private int menuTopShadowAllHeight;
	private int topScreenPosY;
	private int menuBottomViewHeight;
	private int bottomToolbarPosY;
	private int menuFullHeightMax;
	private int menuTitleHeight;
	private int shadowHeight;
	private int screenHeight;
	private int viewHeight;

	private MapActivity getMapActivity() {
		return (MapActivity) getActivity();
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		MapActivity mapActivity = getMapActivity();
		processScreenHeight(container);

		portrait = AndroidUiHelper.isOrientationPortrait(mapActivity);

		menu = mapActivity.getMapLayers().getMapControlsLayer().getMapRouteInfoMenu();
		shadowHeight = AndroidUtils.dpToPx(mapActivity, SHADOW_HEIGHT_TOP_DP);
		topScreenPosY = addStatusBarHeightIfNeeded(0);
		bottomToolbarPosY = addStatusBarHeightIfNeeded(getResources().getDimensionPixelSize(R.dimen.dashboard_map_toolbar));
		minHalfY = viewHeight - (int) (viewHeight * .75f);

		view = inflater.inflate(R.layout.plan_route_info, container, false);
		if (menu == null) {
			return view;
		}
		AndroidUtils.addStatusBarPadding21v(getActivity(), view);

		mainView = view.findViewById(R.id.main_view);
		nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();

		view.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		toolbarContainer = view.findViewById(R.id.context_menu_toolbar_container);
		View toolbarBackButton = view.findViewById(R.id.context_menu_toolbar_back);
		updateVisibility(toolbarContainer, 0);
		toolbarBackButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				openMenuHeaderOnly();
			}
		});

		LockableScrollView bottomScrollView = (LockableScrollView) view.findViewById(R.id.route_menu_bottom_scroll);
		bottomScrollView.setScrollingEnabled(false);
		bottomScrollView.setBackgroundColor(getResources().getColor(nightMode ? R.color.activity_background_dark : R.color.activity_background_light));

		FrameLayout bottomContainer = (FrameLayout) view.findViewById(R.id.bottom_container);
		if (!menu.isRouteCalculated()) {
			bottomContainer.setForeground(getContentIcon(R.drawable.bg_contextmenu_shadow));
		} else {
			bottomContainer.setForeground(null);
		}

		view.findViewById(R.id.route_menu_bottom_view).setBackgroundColor(getResources().getColor(nightMode ? R.color.activity_background_dark : R.color.activity_background_light));

		buildBottomView();

		if (!portrait) {
			final TypedValue typedValueAttr = new TypedValue();
			getMapActivity().getTheme().resolveAttribute(R.attr.left_menu_view_bg, typedValueAttr, true);
			mainView.setBackgroundResource(typedValueAttr.resourceId);
			mainView.setLayoutParams(new FrameLayout.LayoutParams(getResources().getDimensionPixelSize(R.dimen.dashboard_land_width), ViewGroup.LayoutParams.MATCH_PARENT));

			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(AndroidUtils.dpToPx(getMyApplication(), 345f), ViewGroup.LayoutParams.WRAP_CONTENT);

			params.gravity = Gravity.BOTTOM;
			view.findViewById(R.id.control_buttons).setLayoutParams(params);
		}

		runLayoutListener();

		final GestureDetector swipeDetector = new GestureDetector(getMapActivity(), new HorizontalSwipeConfirm(true));

		final View.OnTouchListener slideTouchListener = new View.OnTouchListener() {
			private float dy;
			private float dyMain;
			private float mDownY;

			private int minimumVelocity;
			private int maximumVelocity;
			private VelocityTracker velocityTracker;
			private OverScroller scroller;

			private boolean slidingUp;
			private boolean slidingDown;

			private boolean hasMoved;

			{
				scroller = new OverScroller(getMapActivity());
				final ViewConfiguration configuration = ViewConfiguration.get(getMapActivity());
				minimumVelocity = configuration.getScaledMinimumFlingVelocity();
				maximumVelocity = configuration.getScaledMaximumFlingVelocity();
			}

			@Override
			public boolean onTouch(View v, MotionEvent event) {

				if (!portrait) {
					if (swipeDetector.onTouchEvent(event)) {
						menu.hide();

						recycleVelocityTracker();
						return true;
					}
				}

				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						hasMoved = false;
						mDownY = event.getRawY();
						dy = event.getY();
						dyMain = getViewY();

						initOrResetVelocityTracker();
						velocityTracker.addMovement(event);
						break;

					case MotionEvent.ACTION_MOVE:
						if (Math.abs(event.getRawY() - mDownY) > mainView.getTouchSlop()) {
							moving = true;
						}
						if (moving) {
							hasMoved = true;
							float y = event.getY();
							float newY = getViewY() + (y - dy);
							if (!portrait && newY > topScreenPosY) {
								newY = topScreenPosY;
							}
							setViewY((int) newY, false, false);

							menuFullHeight = view.getHeight() - (int) newY + 10;
							ViewGroup.LayoutParams lp = mainView.getLayoutParams();
							lp.height = Math.max(menuFullHeight, menuTitleHeight);
							mainView.setLayoutParams(lp);
							mainView.requestLayout();

							float newEventY = newY - (dyMain - dy);
							MotionEvent ev = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(),
									event.getX(), newEventY, event.getMetaState());

							initVelocityTrackerIfNotExists();
							velocityTracker.addMovement(ev);

							updateToolbar();
						}

						break;

					case MotionEvent.ACTION_UP:
						if (moving) {
							moving = false;
							hasMoved = false;
							int currentY = getViewY();

							final VelocityTracker velocityTracker = this.velocityTracker;
							velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
							int initialVelocity = (int) velocityTracker.getYVelocity();

							if ((Math.abs(initialVelocity) > minimumVelocity)) {

								scroller.abortAnimation();
								scroller.fling(0, currentY, 0, initialVelocity, 0, 0,
										Math.min(viewHeight - menuFullHeightMax, getFullScreenTopPosY()),
										screenHeight,
										0, 0);
								currentY = scroller.getFinalY();
								scroller.abortAnimation();

								slidingUp = initialVelocity < -2000;
								slidingDown = initialVelocity > 2000;
							} else {
								slidingUp = false;
								slidingDown = false;
							}

							changeMenuState(currentY, slidingUp, slidingDown);
						}
						recycleVelocityTracker();
						break;
					case MotionEvent.ACTION_CANCEL:
						moving = false;
						hasMoved = false;
						recycleVelocityTracker();
						break;

				}
				return true;
			}

			private void initOrResetVelocityTracker() {
				if (velocityTracker == null) {
					velocityTracker = VelocityTracker.obtain();
				} else {
					velocityTracker.clear();
				}
			}

			private void initVelocityTrackerIfNotExists() {
				if (velocityTracker == null) {
					velocityTracker = VelocityTracker.obtain();
					velocityTracker.clear();
				}
			}

			private void recycleVelocityTracker() {
				if (velocityTracker != null) {
					velocityTracker.recycle();
					velocityTracker = null;
				}
			}
		};

		((InterceptorLinearLayout) mainView).setListener(slideTouchListener);
		mainView.setOnTouchListener(slideTouchListener);

		containerLayoutListener = new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View view, int left, int top, int right, int bottom,
			                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
				if (forceUpdateLayout || bottom != oldBottom) {
					forceUpdateLayout = false;
					processScreenHeight(view.getParent());
					runLayoutListener();
				}
			}
		};

		updateInfo();

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (menu == null) {
			dismiss();
		}
		ViewParent parent = view.getParent();
		if (parent != null && containerLayoutListener != null) {
			((View) parent).addOnLayoutChangeListener(containerLayoutListener);
		}
		getMapActivity().getMapLayers().getMapControlsLayer().showMapControlsIfHidden();
	}

	@Override
	public void onPause() {
		if (view != null) {
			ViewParent parent = view.getParent();
			if (parent != null && containerLayoutListener != null) {
				((View) parent).removeOnLayoutChangeListener(containerLayoutListener);
			}
		}
		super.onPause();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (menu != null) {
			menu.onDismiss();
		}
	}

	@Override
	public int getStatusBarColorId() {
		if (menu != null && (menu.getCurrentMenuState() == MenuController.MenuState.FULL_SCREEN || !portrait)) {
			return nightMode ? R.color.status_bar_dark : R.color.status_bar_route_light;
		}
		return -1;
	}

	private void buildBottomView() {
		if (view != null) {
			LinearLayout bottomView = view.findViewById(R.id.route_menu_bottom_view);
			View view = getMapActivity().getLayoutInflater().inflate(R.layout.route_info_statistic, bottomView);
			view.setBackgroundColor(ContextCompat.getColor(getMapActivity(), nightMode ? R.color.route_info_bg_dark : R.color.route_info_bg_light));
		}
	}

	private int getViewY() {
		return (int) mainView.getY();
	}

	private void setViewY(int y, boolean animated, boolean adjustMapPos) {
		mainView.setY(y);
	}

	private void processScreenHeight(ViewParent parent) {
		View container = (View) parent;
		screenHeight = container.getHeight() + AndroidUtils.getStatusBarHeight(getMapActivity());
		viewHeight = screenHeight - AndroidUtils.getStatusBarHeight(getMapActivity());
	}

	private int getFullScreenTopPosY() {
		return -menuTitleHeight + bottomToolbarPosY;
	}

	private int addStatusBarHeightIfNeeded(int res) {
		if (Build.VERSION.SDK_INT >= 21) {
			return res + AndroidUtils.getStatusBarHeight(getMyApplication());
		}
		return res;
	}

	private int getHeaderOnlyTopY() {
		return viewHeight - menuTitleHeight - AndroidUtils.dpToPx(getMyApplication(), 48f);
	}

	public void openMenuFullScreen() {
		changeMenuState(getMenuStatePosY(MenuController.MenuState.FULL_SCREEN), false, false);
	}

	public void openMenuHeaderOnly() {
		if (portrait) {
			changeMenuState(getMenuStatePosY(MenuController.MenuState.HEADER_ONLY), false, false);
		}
	}

	public void openMenuHalfScreen() {
		if (portrait) {
			changeMenuState(getMenuStatePosY(MenuController.MenuState.HALF_SCREEN), false, false);
		}
	}

	private int getMenuStatePosY(int menuState) {
		if (!portrait) {
			return topScreenPosY;
		}
		switch (menuState) {
			case MapRouteInfoMenu.MenuState.HEADER_ONLY:
				return getHeaderOnlyTopY();
			case MapRouteInfoMenu.MenuState.HALF_SCREEN:
				return minHalfY;
			case MapRouteInfoMenu.MenuState.FULL_SCREEN:
				return getFullScreenTopPosY();
			default:
				return 0;
		}
	}

	private void changeMenuState(int currentY, boolean slidingUp, boolean slidingDown) {
		boolean needCloseMenu = false;

		int currentMenuState = menu.getCurrentMenuState();
		if (portrait) {
			int headerDist = Math.abs(currentY - getMenuStatePosY(MapRouteInfoMenu.MenuState.HEADER_ONLY));
			int halfDist = Math.abs(currentY - getMenuStatePosY(MapRouteInfoMenu.MenuState.HALF_SCREEN));
			int fullDist = Math.abs(currentY - getMenuStatePosY(MapRouteInfoMenu.MenuState.FULL_SCREEN));
			int newState;
			if (headerDist < halfDist && headerDist < fullDist) {
				newState = MenuController.MenuState.HEADER_ONLY;
			} else if (halfDist < headerDist && halfDist < fullDist) {
				newState = MenuController.MenuState.HALF_SCREEN;
			} else {
				newState = MenuController.MenuState.FULL_SCREEN;
			}

			if (slidingDown && currentMenuState == MenuController.MenuState.FULL_SCREEN && getViewY() < getFullScreenTopPosY()) {
				slidingDown = false;
				newState = MenuController.MenuState.FULL_SCREEN;
			}
			if (menuBottomViewHeight > 0 && slidingUp) {
				while (menu.getCurrentMenuState() != newState) {
					if (!menu.slideUp()) {
						break;
					}
				}
			} else if (slidingDown) {
				if (currentMenuState == MenuController.MenuState.HEADER_ONLY) {
					needCloseMenu = true;
				} else {
					while (menu.getCurrentMenuState() != newState) {
						if (!menu.slideDown()) {
							needCloseMenu = true;
							break;
						}
					}
				}
			} else {
				if (currentMenuState < newState) {
					while (menu.getCurrentMenuState() != newState) {
						if (!menu.slideUp()) {
							break;
						}
					}
				} else {
					while (menu.getCurrentMenuState() != newState) {
						if (!menu.slideDown()) {
							break;
						}
					}
				}
			}
		}
		int newMenuState = menu.getCurrentMenuState();
		boolean needMapAdjust = currentMenuState != newMenuState && newMenuState != MenuController.MenuState.FULL_SCREEN;

		applyPosY(currentY, needCloseMenu, needMapAdjust, currentMenuState, newMenuState, 0);
	}


	private int getPosY(final int currentY, boolean needCloseMenu, int previousState) {
		if (needCloseMenu) {
			return screenHeight;
		}

		int destinationState;
		destinationState = menu.getCurrentMenuState();

		int posY = 0;
		switch (destinationState) {
			case MenuController.MenuState.HEADER_ONLY:
				posY = getMenuStatePosY(MenuController.MenuState.HEADER_ONLY);
				break;
			case MenuController.MenuState.HALF_SCREEN:
				posY = getMenuStatePosY(MenuController.MenuState.HALF_SCREEN);
				break;
			case MenuController.MenuState.FULL_SCREEN:
				if (currentY != CURRENT_Y_UNDEFINED) {
					int maxPosY = viewHeight - menuFullHeightMax;
					int minPosY = getMenuStatePosY(MenuController.MenuState.FULL_SCREEN);
					if (maxPosY > minPosY) {
						maxPosY = minPosY;
					}
					if (currentY > minPosY || previousState != MenuController.MenuState.FULL_SCREEN) {
						posY = minPosY;
					} else if (currentY < maxPosY) {
						posY = maxPosY;
					} else {
						posY = currentY;
					}
				} else {
					posY = getMenuStatePosY(MenuController.MenuState.FULL_SCREEN);
				}
				break;
			default:
				break;
		}
		if (portrait) {
			getMapActivity().updateStatusBarColor();
		}
		return posY;
	}

	private void updateMainViewLayout(int posY) {
		if (view != null) {
			menuFullHeight = view.getHeight() - posY;
			menuTopShadowAllHeight = menuTitleHeight;
			ViewGroup.LayoutParams lp = mainView.getLayoutParams();
			lp.height = Math.max(menuFullHeight, menuTitleHeight);
			mainView.setLayoutParams(lp);
			mainView.requestLayout();
		}
	}

	private void applyPosY(final int currentY, final boolean needCloseMenu, boolean needMapAdjust,
	                       final int previousMenuState, final int newMenuState, int dZoom) {
		final int posY = getPosY(currentY, needCloseMenu, previousMenuState);
		if (getViewY() != posY || dZoom != 0) {
			if (posY < getViewY()) {
				updateMainViewLayout(posY);
			}

			final float toolbarAlpha = getToolbarAlpha(posY);
			if (toolbarAlpha > 0) {
				updateVisibility(toolbarContainer, true);
			}
			toolbarContainer.animate().alpha(toolbarAlpha)
					.setDuration(200)
					.setInterpolator(new DecelerateInterpolator())
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							updateVisibility(toolbarContainer, toolbarAlpha);
						}
					})
					.start();

			mainView.animate().y(posY)
					.setDuration(200)
					.setInterpolator(new DecelerateInterpolator())
					.setListener(new AnimatorListenerAdapter() {

						boolean canceled = false;

						@Override
						public void onAnimationCancel(Animator animation) {
							canceled = true;
						}

						@Override
						public void onAnimationEnd(Animator animation) {
							if (!canceled) {
								if (needCloseMenu) {
									menu.hide();
								} else {
									updateMainViewLayout(posY);
									if (previousMenuState != 0 && newMenuState != 0 && previousMenuState != newMenuState) {
										doAfterMenuStateChange(previousMenuState, newMenuState);
									}
								}
							}
						}
					})
					.start();

		}
	}

	private void doAfterMenuStateChange(int previousState, int newState) {
		runLayoutListener();
	}

	private float getToolbarAlpha(int y) {
		float a = 0;
		if (menu != null && portrait) {
			if (y < bottomToolbarPosY) {
				a = 1f - (y - topScreenPosY) * (1f / (bottomToolbarPosY - topScreenPosY));
			}
			if (a < 0) {
				a = 0;
			} else if (a > 1) {
				a = 1;
			}
		}
		return a;
	}

	private void updateToolbar() {
		float a = getToolbarAlpha(getViewY());
		updateVisibility(toolbarContainer, a);
	}

	private void updateVisibility(View v, float alpha) {
		boolean visible = alpha > 0;
		v.setAlpha(alpha);
		if (visible && v.getVisibility() != View.VISIBLE) {
			v.setVisibility(View.VISIBLE);
		} else if (!visible && v.getVisibility() == View.VISIBLE) {
			v.setVisibility(View.INVISIBLE);
		}
	}

	private void updateVisibility(View v, boolean visible) {
		if (visible && v.getVisibility() != View.VISIBLE) {
			v.setVisibility(View.VISIBLE);
		} else if (!visible && v.getVisibility() == View.VISIBLE) {
			v.setVisibility(View.INVISIBLE);
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void runLayoutListener() {
		if (view != null) {
			ViewTreeObserver vto = view.getViewTreeObserver();
			vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

				@Override
				public void onGlobalLayout() {

					if (view != null) {
						ViewTreeObserver obs = view.getViewTreeObserver();
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
							obs.removeOnGlobalLayoutListener(this);
						} else {
							obs.removeGlobalOnLayoutListener(this);
						}

						if (getActivity() == null) {
							return;
						}

						menuFullHeight = view.findViewById(R.id.main_view).getHeight();
						int newMenuTopShadowAllHeight = view.findViewById(R.id.route_menu_top_shadow_all).getHeight();

						int dy = shadowHeight;

						menuTopShadowAllHeight = newMenuTopShadowAllHeight;
						menuTitleHeight = menuTopShadowAllHeight + dy;
						menuBottomViewHeight = view.findViewById(R.id.route_menu_bottom_view).getHeight();

						menuFullHeightMax = menuTitleHeight + menuBottomViewHeight;

						if (!moving) {
							doLayoutMenu();
						}
						initLayout = false;
					}
				}

			});
		}
	}

	private void doLayoutMenu() {
		final int posY = getPosY(getViewY(), false, menu.getCurrentMenuState());
		setViewY(posY, true, !initLayout);
		updateMainViewLayout(posY);
	}

	public int getHeight() {
		if (mainView != null) {
			return mainView.getHeight();
		} else {
			return 0;
		}
	}

	public int getWidth() {
		if (mainView != null) {
			return mainView.getWidth();
		} else {
			return 0;
		}
	}

	public void updateInfo() {
		if (menu != null) {
			menu.updateInfo(view);
			applyDayNightMode();
			runLayoutListener();
		}
	}

	public void updateFromIcon() {
		if (menu != null) {
			menu.updateFromIcon(mainView);
		}
	}

	public void updateRouteCalculationProgress(int progress) {
		ProgressBar progressBar = (ProgressBar) mainView.findViewById(R.id.progress_bar);
		if (progressBar != null) {
			if (progressBar.getVisibility() != View.VISIBLE) {
				progressBar.setVisibility(View.VISIBLE);
			}
			progressBar.setProgress(progress);
		}
	}

	public void hideRouteCalculationProgressBar() {
		View progressBar = mainView.findViewById(R.id.progress_bar);
		if (progressBar != null) {
			progressBar.setVisibility(View.GONE);
		}
	}

	public void updateControlButtons() {
		OsmandApplication app = getMyApplication();
		if (app != null) {
			if (menu.isRouteCalculated()) {
				AndroidUtils.setBackground(app, view.findViewById(R.id.start_button), nightMode,
						R.color.active_buttons_and_links_light, R.color.active_buttons_and_links_dark);
				int color = nightMode ? R.color.main_font_dark : R.color.card_and_list_background_light;
				((TextView) view.findViewById(R.id.start_button_descr)).setTextColor(ContextCompat.getColor(getMapActivity(), color));
				((ImageView) view.findViewById(R.id.start_icon)).setImageDrawable(app.getUIUtilities().getIcon(R.drawable.ic_action_start_navigation, color));
			} else {
				AndroidUtils.setBackground(app, view.findViewById(R.id.start_button), nightMode,
						R.color.activity_background_light, R.color.route_info_cancel_button_color_dark);
				int color = R.color.description_font_and_bottom_sheet_icons;
				((TextView) view.findViewById(R.id.start_button_descr)).setTextColor(ContextCompat.getColor(getMapActivity(), color));
				((ImageView) view.findViewById(R.id.start_icon)).setImageDrawable(app.getUIUtilities().getIcon(R.drawable.ic_action_start_navigation, color));
			}
		}
	}

	public void show(MapActivity mapActivity) {
		int slideInAnim = 0;
		int slideOutAnim = 0;
		if (!mapActivity.getMyApplication().getSettings().DO_NOT_USE_ANIMATIONS.get()) {
			slideInAnim = R.anim.slide_in_bottom;
			slideOutAnim = R.anim.slide_out_bottom;
		}

		mapActivity.getSupportFragmentManager()
				.beginTransaction()
				.setCustomAnimations(slideInAnim, slideOutAnim, slideInAnim, slideOutAnim)
				.add(R.id.routeMenuContainer, this, TAG)
				.addToBackStack(TAG)
				.commitAllowingStateLoss();
	}

	public void dismiss() {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			try {
				activity.getSupportFragmentManager().popBackStack(TAG,
						FragmentManager.POP_BACK_STACK_INCLUSIVE);
			} catch (Exception e) {
				//
			}
		}
	}

	public void applyDayNightMode() {
		MapActivity ctx = getMapActivity();
		boolean portraitMode = AndroidUiHelper.isOrientationPortrait(ctx);
		boolean landscapeLayout = !portraitMode;
		boolean nightMode = ctx.getMyApplication().getDaynightHelper().isNightModeForMapControls();
		if (!landscapeLayout) {
			AndroidUtils.setBackground(ctx, mainView, nightMode, R.drawable.route_info_menu_bg_light, R.drawable.route_info_menu_bg_dark);
		} else {
			AndroidUtils.setBackground(ctx, mainView, nightMode, R.drawable.route_info_menu_bg_left_light, R.drawable.route_info_menu_bg_left_dark);
		}
		AndroidUtils.setBackground(ctx, mainView.findViewById(R.id.dividerFromDropDown), nightMode,
				R.color.route_info_divider_light, R.color.route_info_divider_dark);
		AndroidUtils.setBackground(ctx, mainView.findViewById(R.id.viaLayoutDivider), nightMode,
				R.color.route_info_divider_light, R.color.route_info_divider_dark);
		AndroidUtils.setBackground(ctx, mainView.findViewById(R.id.dividerButtons), nightMode,
				R.color.route_info_divider_light, R.color.route_info_divider_dark);
		AndroidUtils.setBackground(ctx, mainView.findViewById(R.id.app_modes_options_container), nightMode,
				R.drawable.route_info_trans_gradient_light, R.drawable.route_info_trans_gradient_dark);
		AndroidUtils.setBackground(ctx, mainView.findViewById(R.id.dividerToDropDown), nightMode,
				R.color.route_info_divider_light, R.color.route_info_divider_dark);
		AndroidUtils.setBackground(ctx, view.findViewById(R.id.dividerControlButtons), nightMode,
				R.color.route_info_divider_light, R.color.route_info_divider_dark);
		AndroidUtils.setBackground(ctx, mainView.findViewById(R.id.info_divider), nightMode,
				R.color.activity_background_light, R.color.route_info_cancel_button_color_dark);
		AndroidUtils.setBackground(ctx, mainView.findViewById(R.id.route_info_details_card), nightMode,
				R.color.activity_background_light, R.color.route_info_cancel_button_color_dark);
		AndroidUtils.setBackground(ctx, mainView.findViewById(R.id.RouteInfoControls), nightMode,
				R.color.route_info_bg_light, R.color.route_info_bg_dark);

		int color = ContextCompat.getColor(getMapActivity(), nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light);

		((TextView) mainView.findViewById(R.id.from_button_description)).setTextColor(color);
		((TextView) mainView.findViewById(R.id.via_button_description)).setTextColor(color);
		((TextView) mainView.findViewById(R.id.to_button_description)).setTextColor(color);
		((TextView) mainView.findViewById(R.id.sound_setting_button_descr)).setTextColor(color);
		((TextView) mainView.findViewById(R.id.map_options_route_button)).setTextColor(color);

		((TextView) view.findViewById(R.id.cancel_button_descr)).setTextColor(
				ContextCompat.getColor(getMapActivity(), nightMode ? R.color.active_buttons_and_links_dark : R.color.route_info_cancel_button_color_light));

		AndroidUtils.setTextPrimaryColor(ctx, (TextView) mainView.findViewById(R.id.ViaView), nightMode);
		AndroidUtils.setTextSecondaryColor(ctx, (TextView) mainView.findViewById(R.id.ViaSubView), nightMode);
		AndroidUtils.setTextSecondaryColor(ctx, (TextView) mainView.findViewById(R.id.toTitle), nightMode);
		AndroidUtils.setTextSecondaryColor(ctx, (TextView) mainView.findViewById(R.id.fromTitle), nightMode);

		ctx.setupRouteCalculationProgressBar((ProgressBar) mainView.findViewById(R.id.progress_bar));

		FrameLayout bottomContainer = (FrameLayout) view.findViewById(R.id.bottom_container);
		if (!menu.isRouteCalculated()) {
			bottomContainer.setForeground(getContentIcon(R.drawable.bg_contextmenu_shadow));
		} else {
			bottomContainer.setForeground(null);
		}

		updateControlButtons();
	}

	public static boolean showInstance(final MapActivity mapActivity) {
		boolean portrait = AndroidUiHelper.isOrientationPortrait(mapActivity);
		int slideInAnim = 0;
		int slideOutAnim = 0;
		if (!mapActivity.getMyApplication().getSettings().DO_NOT_USE_ANIMATIONS.get()) {
			if (portrait) {
				slideInAnim = R.anim.slide_in_bottom;
				slideOutAnim = R.anim.slide_out_bottom;
			} else {
				slideInAnim = R.anim.slide_in_left;
				slideOutAnim = R.anim.slide_out_left;
			}
		}

		try {
			mapActivity.getContextMenu().hideMenues();

			MapRouteInfoMenuFragment fragment = new MapRouteInfoMenuFragment();
			mapActivity.getSupportFragmentManager()
					.beginTransaction()
					.setCustomAnimations(slideInAnim, slideOutAnim, slideInAnim, slideOutAnim)
					.add(R.id.routeMenuContainer, fragment, TAG)
					.addToBackStack(TAG)
					.commitAllowingStateLoss();

			return true;

		} catch (RuntimeException e) {
			return false;
		}
	}
}
