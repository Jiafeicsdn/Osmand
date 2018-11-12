package net.osmand.plus.mapcontextmenu.other;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.ListPopupWindow;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;

import net.osmand.AndroidUtils;
import net.osmand.Location;
import net.osmand.ValueHolder;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.ApplicationMode;
import net.osmand.plus.GPXUtilities;
import net.osmand.plus.GeocodingLookupService;
import net.osmand.plus.GeocodingLookupService.AddressLookupRequest;
import net.osmand.plus.GpxSelectionHelper;
import net.osmand.plus.MapMarkersHelper;
import net.osmand.plus.MapMarkersHelper.MapMarker;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.OsmandSettings.OsmandPreference;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper;
import net.osmand.plus.TargetPointsHelper.TargetPoint;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.ShowRouteInfoDialogFragment;
import net.osmand.plus.activities.actions.AppModeDialog;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.helpers.MapMarkerDialogHelper;
import net.osmand.plus.mapcontextmenu.MapContextMenu;
import net.osmand.plus.mapmarkers.MapMarkerSelectionFragment;
import net.osmand.plus.routing.RouteDirectionInfo;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.routing.RoutingHelper.IRouteInformationListener;
import net.osmand.plus.views.MapControlsLayer;
import net.osmand.plus.views.OsmandMapTileView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static net.osmand.binary.RouteDataObject.HEIGHT_UNDEFINED;

public class MapRouteInfoMenu implements IRouteInformationListener {

	public static class MenuState {
		public static final int HEADER_ONLY = 1;
		public static final int HALF_SCREEN = 2;
		public static final int FULL_SCREEN = 4;
	}

	public static int directionInfo = -1;
	public static boolean controlVisible = false;
	private final MapContextMenu contextMenu;
	private final RoutingHelper routingHelper;
	private OsmandMapTileView mapView;
	private GeocodingLookupService geocodingLookupService;
	private boolean selectFromMapTouch;
	private boolean selectFromMapForTarget;
	private boolean selectFromMapForIntermediate;

	private boolean showMenu = false;
	private static boolean visible;
	private MapActivity mapActivity;
	private OsmandApplication app;
	private MapControlsLayer mapControlsLayer;
	public static final String TARGET_SELECT = "TARGET_SELECT";
	private boolean nightMode;
	private boolean switched;

	private AddressLookupRequest startPointRequest;
	private AddressLookupRequest targetPointRequest;
	private List<LatLon> intermediateRequestsLatLon = new ArrayList<>();
	private OnDismissListener onDismissListener;

	private OnMarkerSelectListener onMarkerSelectListener;
	private View mainView;

	private int currentMenuState;
	private boolean portraitMode;
	private GPXUtilities.GPXFile gpx;
	private RoutingHelper helper;
	private View view;
	private ListView listView;
	private GpxUiHelper.OrderedLineDataSet elevationDataSet;
	private GpxUiHelper.OrderedLineDataSet slopeDataSet;
	private GpxSelectionHelper.GpxDisplayItem gpxItem;
	private boolean hasHeights;

	private static final long SPINNER_MY_LOCATION_ID = 1;
	public static final long SPINNER_FAV_ID = 2;
	public static final long SPINNER_MAP_ID = 3;
	public static final long SPINNER_ADDRESS_ID = 4;
	private static final long SPINNER_START_ID = 5;
	private static final long SPINNER_FINISH_ID = 6;
	private static final long SPINNER_HINT_ID = 100;
	public static final long SPINNER_MAP_MARKER_1_ID = 301;
	public static final long SPINNER_MAP_MARKER_2_ID = 302;
	private static final long SPINNER_MAP_MARKER_3_ID = 303;
	public static final long SPINNER_MAP_MARKER_MORE_ID = 350;

	public interface OnMarkerSelectListener {
		void onSelect(int index, boolean target, boolean intermediate);
	}

	public MapRouteInfoMenu(MapActivity mapActivity, MapControlsLayer mapControlsLayer) {
		this.mapActivity = mapActivity;
		this.app = mapActivity.getMyApplication();
		this.mapControlsLayer = mapControlsLayer;
		contextMenu = mapActivity.getContextMenu();
		routingHelper = mapActivity.getRoutingHelper();
		mapView = mapActivity.getMapView();
		routingHelper.addListener(this);
		portraitMode = AndroidUiHelper.isOrientationPortrait(mapActivity);
		currentMenuState = getInitialMenuState();

		geocodingLookupService = mapActivity.getMyApplication().getGeocodingLookupService();
		onMarkerSelectListener = new OnMarkerSelectListener() {
			@Override
			public void onSelect(int index, boolean target, boolean intermediate) {
				selectMapMarker(index, target, intermediate);
			}
		};
	}

	private int getInitialMenuState() {
		if (!portraitMode) {
			return MenuState.FULL_SCREEN;
		} else {
			return getInitialMenuStatePortrait();
		}
	}

	public OnDismissListener getOnDismissListener() {
		return onDismissListener;
	}

	public void setOnDismissListener(OnDismissListener onDismissListener) {
		this.onDismissListener = onDismissListener;
	}

	public boolean isSelectFromMapTouch() {
		return selectFromMapTouch;
	}

	public void cancelSelectionFromMap() {
		selectFromMapTouch = false;
	}

	public boolean onSingleTap(PointF point, RotatedTileBox tileBox) {
		if (selectFromMapTouch) {
			LatLon latlon = tileBox.getLatLonFromPixel(point.x, point.y);
			selectFromMapTouch = false;
			if (selectFromMapForIntermediate) {
				getTargets().navigateToPoint(latlon, true, getTargets().getIntermediatePoints().size());
			} else if (selectFromMapForTarget) {
				getTargets().navigateToPoint(latlon, true, -1);
			} else {
				getTargets().setStartPoint(latlon, true, null);
			}
			show();
			if (selectFromMapForIntermediate && getTargets().checkPointToNavigateShort()) {
				mapActivity.getMapActions().openIntermediatePointsDialog();
			}
			return true;
		}
		return false;
	}

	public OnMarkerSelectListener getOnMarkerSelectListener() {
		return onMarkerSelectListener;
	}

	private void cancelStartPointAddressRequest() {
		if (startPointRequest != null) {
			geocodingLookupService.cancel(startPointRequest);
			startPointRequest = null;
		}
	}

	private void cancelTargetPointAddressRequest() {
		if (targetPointRequest != null) {
			geocodingLookupService.cancel(targetPointRequest);
			targetPointRequest = null;
		}
	}

	public void setVisible(boolean visible) {
		if (visible) {
			if (showMenu) {
				show();
				showMenu = false;
			}
			controlVisible = true;
		} else {
			hide();
			controlVisible = false;
		}
	}


	public int getCurrentMenuState() {
		return currentMenuState;
	}

	public int getSupportedMenuStates() {
		if (!portraitMode) {
			return MenuState.FULL_SCREEN;
		} else {
			return getSupportedMenuStatesPortrait();
		}
	}

	protected int getInitialMenuStatePortrait() {
		return MenuState.HEADER_ONLY;
	}

	protected int getSupportedMenuStatesPortrait() {
		return MenuState.HEADER_ONLY | MenuState.HALF_SCREEN | MenuState.FULL_SCREEN;
	}

	public boolean slideUp() {
		int v = currentMenuState;
		for (int i = 0; i < 2; i++) {
			v = v << 1;
			if ((v & getSupportedMenuStates()) != 0) {
				currentMenuState = v;
				return true;
			}
		}
		return false;
	}

	public boolean slideDown() {
		int v = currentMenuState;
		for (int i = 0; i < 2; i++) {
			v = v >> 1;
			if ((v & getSupportedMenuStates()) != 0) {
				currentMenuState = v;
				return true;
			}
		}
		return false;
	}

	public void showHideMenu() {
		intermediateRequestsLatLon.clear();
		if (isVisible()) {
			hide();
		} else {
			show();
		}
	}

	public void updateRouteCalculationProgress(int progress) {
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = findMenuFragment();
		if (fragmentRef != null) {
			fragmentRef.get().updateRouteCalculationProgress(progress);
			fragmentRef.get().updateControlButtons();
		}
	}

	public void routeCalculationFinished() {
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = findMenuFragment();
		if (fragmentRef != null) {
			fragmentRef.get().hideRouteCalculationProgressBar();
			fragmentRef.get().updateControlButtons();
		}
	}

	public void updateMenu() {
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = findMenuFragment();
		if (fragmentRef != null)
			fragmentRef.get().updateInfo();
	}

	public void updateFromIcon() {
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = findMenuFragment();
		if (fragmentRef != null)
			fragmentRef.get().updateFromIcon();
	}

	public void updateInfo(final View main) {
		mainView = main;
		nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();
		updateViaView(main);
		updateFromSpinner(main);
		updateToSpinner(main);
		updateApplicationModes(main);
		updateApplicationModesOptions(main);
		updateControlsButtons(main);

		if (isRouteCalculated()) {
			makeGpx();
			updateRouteButtons(main);
		} else {
			updateRouteCalcProgress(main);
		}
	}

	public boolean isRouteCalculated() {
		return routingHelper.isRouteCalculated();
	}

	private void updateApplicationModesOptions(final View parentView) {
		parentView.findViewById(R.id.app_modes_options).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				availableProfileDialog();
			}
		});
	}

	private void availableProfileDialog() {
		AlertDialog.Builder b = new AlertDialog.Builder(mapActivity);
		final OsmandSettings settings = mapActivity.getMyApplication().getSettings();
		final List<ApplicationMode> modes = ApplicationMode.allPossibleValues();
		modes.remove(ApplicationMode.DEFAULT);
		final Set<ApplicationMode> selected = new LinkedHashSet<ApplicationMode>(ApplicationMode.values(mapActivity.getMyApplication()));
		selected.remove(ApplicationMode.DEFAULT);
		View v = AppModeDialog.prepareAppModeView(mapActivity, modes, selected, null, false, true, false,
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						StringBuilder vls = new StringBuilder(ApplicationMode.DEFAULT.getStringKey() + ",");
						for (ApplicationMode mode : modes) {
							if (selected.contains(mode)) {
								vls.append(mode.getStringKey()).append(",");
							}
						}
						settings.AVAILABLE_APP_MODES.set(vls.toString());
					}
				});
		b.setTitle(R.string.profile_settings);
		b.setPositiveButton(R.string.shared_string_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				updateApplicationModes(mainView);
			}
		});
		b.setView(v);
		b.show();
	}

	private void updateApplicationMode(ApplicationMode mode, ApplicationMode next) {
		OsmandPreference<ApplicationMode> appMode
				= mapActivity.getMyApplication().getSettings().APPLICATION_MODE;
		if (routingHelper.isFollowingMode() && appMode.get() == mode) {
			appMode.set(next);
			//updateMenu();
		}
		routingHelper.setAppMode(next);
		mapActivity.getMyApplication().initVoiceCommandPlayer(mapActivity, next, true, null, false, false);
		routingHelper.recalculateRouteDueToSettingsChange();
	}

	private void updateRouteCalcProgress(final View main) {
		main.findViewById(R.id.route_info_details_card).setVisibility(View.GONE);
	}

	private void updateApplicationModes(final View parentView) {
		//final OsmandSettings settings = mapActivity.getMyApplication().getSettings();
		//ApplicationMode am = settings.APPLICATION_MODE.get();
		final ApplicationMode am = routingHelper.getAppMode();
		final Set<ApplicationMode> selected = new HashSet<>();
		selected.add(am);
		ViewGroup vg = (ViewGroup) parentView.findViewById(R.id.app_modes);
		vg.removeAllViews();
		View.OnClickListener listener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (selected.size() > 0) {
					ApplicationMode next = selected.iterator().next();
					updateApplicationMode(am, next);
				}
			}
		};
		OsmandSettings settings = mapActivity.getMyApplication().getSettings();
		final List<ApplicationMode> values = new ArrayList<ApplicationMode>(ApplicationMode.values(mapActivity.getMyApplication()));
		values.remove(ApplicationMode.DEFAULT);

		if (values.size() > 0 && !values.contains(am)) {
			ApplicationMode next = values.iterator().next();
			updateApplicationMode(am, next);
		}

		View ll = mapActivity.getLayoutInflater().inflate(R.layout.mode_toggles, vg);
		ll.setBackgroundColor(ContextCompat.getColor(mapActivity, nightMode ? R.color.route_info_bg_dark : R.color.route_info_bg_light));

		HorizontalScrollView scrollView = ll.findViewById(R.id.app_modes_scroll_container);
		scrollView.setVerticalScrollBarEnabled(false);
		scrollView.setHorizontalScrollBarEnabled(false);

		final View[] buttons = new View[values.size()];
		int k = 0;
		Iterator<ApplicationMode> iterator = values.iterator();
		while (iterator.hasNext()) {
			ApplicationMode mode = iterator.next();
			View toggle = createToggle(mapActivity.getLayoutInflater(), (OsmandApplication) mapActivity.getApplication(), (LinearLayout) ll.findViewById(R.id.app_modes_content), mode, true);

			if (!iterator.hasNext() && toggle.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
				ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) toggle.getLayoutParams();
				p.setMargins(p.leftMargin, p.topMargin, p.rightMargin + mapActivity.getResources().getDimensionPixelSize(R.dimen.content_padding), p.bottomMargin);
			}

			buttons[k++] = toggle;
		}
		for (int i = 0; i < buttons.length; i++) {
			updateButtonState((OsmandApplication) mapActivity.getApplication(), values, selected, listener, buttons, i, true, true);
		}
	}

	private void makeGpx() {
		double lastHeight = HEIGHT_UNDEFINED;
		gpx = new GPXUtilities.GPXFile();
		List<Location> locations = routingHelper.getRoute().getRouteLocations();
		if (locations != null) {
			GPXUtilities.Track track = new GPXUtilities.Track();
			GPXUtilities.TrkSegment seg = new GPXUtilities.TrkSegment();
			for (Location l : locations) {
				GPXUtilities.WptPt point = new GPXUtilities.WptPt();
				point.lat = l.getLatitude();
				point.lon = l.getLongitude();
				if (l.hasAltitude()) {
					if (!hasHeights) {
						hasHeights = true;
					}
					float h = (float) l.getAltitude();
					point.ele = h;
					if (lastHeight == HEIGHT_UNDEFINED && seg.points.size() > 0) {
						for (GPXUtilities.WptPt pt : seg.points) {
							if (Double.isNaN(pt.ele)) {
								pt.ele = h;
							}
						}
					}
					lastHeight = h;
				}
				seg.points.add(point);
			}
			track.segments.add(seg);
			gpx.tracks.add(track);

			String groupName = mapActivity.getMyApplication().getString(R.string.current_route);
			GpxSelectionHelper.GpxDisplayGroup group = mapActivity.getMyApplication().getSelectedGpxHelper().buildGpxDisplayGroup(gpx, 0, groupName);
			if (group != null && group.getModifiableList().size() > 0) {
				gpxItem = group.getModifiableList().get(0);
				if (gpxItem != null) {
					gpxItem.route = true;
				}
			}
		}
	}

	private void updateControlsButtons(View main) {
		boolean nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();

		View startButton = main.findViewById(R.id.start_button);
		if (isRouteCalculated()) {
			AndroidUtils.setBackground(app, startButton, nightMode, R.color.active_buttons_and_links_light, R.color.active_buttons_and_links_dark);
			int color = nightMode ? R.color.main_font_dark : R.color.card_and_list_background_light;
			((TextView) main.findViewById(R.id.start_button_descr)).setTextColor(ContextCompat.getColor(app, color));
			((ImageView) main.findViewById(R.id.start_icon)).setImageDrawable(app.getUIUtilities().getIcon(R.drawable.ic_action_start_navigation, color));
		} else {
			AndroidUtils.setBackground(app, startButton, nightMode, R.color.activity_background_light, R.color.route_info_cancel_button_color_dark);
			int color = R.color.description_font_and_bottom_sheet_icons;
			((TextView) main.findViewById(R.id.start_button_descr)).setTextColor(ContextCompat.getColor(app, color));
			((ImageView) main.findViewById(R.id.start_icon)).setImageDrawable(app.getUIUtilities().getIcon(R.drawable.ic_action_start_navigation, color));
		}
		startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				clickRouteGo();
			}
		});

		View cancelButton = main.findViewById(R.id.cancel_button);
		AndroidUtils.setBackground(app, cancelButton, nightMode, R.color.card_and_list_background_light, R.color.card_and_list_background_dark);
		main.findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				clickRouteCancel();
			}
		});

		TextView options = (TextView) main.findViewById(R.id.map_options_route_button);
		Drawable drawable = app.getUIUtilities().getIcon(R.drawable.map_action_settings, nightMode ? R.color.route_info_control_icon_color_dark : R.color.route_info_control_icon_color_light);
		if (Build.VERSION.SDK_INT >= 21) {
			Drawable active = app.getUIUtilities().getIcon(R.drawable.map_action_settings, nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light);
			drawable = AndroidUtils.createPressedStateListDrawable(drawable, active);
		}
		options.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
		options.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				clickRouteParams();
			}
		});

		FrameLayout soundButton = mainView.findViewById(R.id.sound_setting_button);

		AndroidUtils.setBackground(app, soundButton, nightMode, R.drawable.btn_border_trans_light, R.drawable.btn_border_trans_dark);
		AndroidUtils.setForeground(app, soundButton, nightMode, R.drawable.ripple_light, R.drawable.ripple_dark);

		final TextView soundOptionDescription = (TextView) main.findViewById(R.id.sound_setting_button_descr);
		String text = app.getString(R.string.sound_is, (app.getText(app.getRoutingHelper().getVoiceRouter().isMute() ? R.string.shared_string_off : R.string.shared_string_on)));
		soundOptionDescription.setText(text);
		Drawable sound = app.getUIUtilities().getIcon(R.drawable.ic_action_volume_up, nightMode ? R.color.route_info_control_icon_color_dark : R.color.route_info_control_icon_color_light);
		if (Build.VERSION.SDK_INT >= 21) {
			Drawable active = app.getUIUtilities().getIcon(R.drawable.ic_action_volume_up, nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light);
			sound = AndroidUtils.createPressedStateListDrawable(sound, active);
		}
		soundOptionDescription.setCompoundDrawablesWithIntrinsicBounds(sound, null, null, null);

		soundButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				boolean mt = !app.getRoutingHelper().getVoiceRouter().isMute();
				app.getSettings().VOICE_MUTE.set(mt);
				app.getRoutingHelper().getVoiceRouter().setMute(mt);
				String text = app.getString(R.string.sound_is, (app.getText(app.getRoutingHelper().getVoiceRouter().isMute() ? R.string.shared_string_off : R.string.shared_string_on)));
				soundOptionDescription.setText(text);
			}
		});
	}

	private void clickRouteGo() {
		if (getTargets().getPointToNavigate() != null) {
			hide();
		}
		mapControlsLayer.startNavigation();
	}

	private void clickRouteCancel() {
		mapControlsLayer.stopNavigation();
	}

	private void clickRouteParams() {
		mapActivity.getMapActions().openRoutePreferencesDialog();
	}

	private void updateRouteButtons(final View mainView) {
		mainView.findViewById(R.id.dividerToDropDown).setVisibility(View.VISIBLE);
		mainView.findViewById(R.id.route_info_details_card).setVisibility(View.VISIBLE);
		final OsmandApplication ctx = mapActivity.getMyApplication();

		View info = mainView.findViewById(R.id.info_container);
		info.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ShowRouteInfoDialogFragment.showDialog(mapActivity.getSupportFragmentManager());
			}
		});

		ImageView infoIcon = (ImageView) mainView.findViewById(R.id.InfoIcon);
		ImageView durationIcon = (ImageView) mainView.findViewById(R.id.DurationIcon);
		View infoDistanceView = mainView.findViewById(R.id.InfoDistance);
		View infoDurationView = mainView.findViewById(R.id.InfoDuration);
		if (directionInfo >= 0) {
			infoIcon.setVisibility(View.GONE);
			durationIcon.setVisibility(View.GONE);
			infoDistanceView.setVisibility(View.GONE);
			infoDurationView.setVisibility(View.GONE);
		} else {
			infoIcon.setImageDrawable(ctx.getUIUtilities().getIcon(R.drawable.ic_action_route_distance, R.color.route_info_unchecked_mode_icon_color));
			infoIcon.setVisibility(View.VISIBLE);
			durationIcon.setImageDrawable(ctx.getUIUtilities().getIcon(R.drawable.ic_action_time_span, R.color.route_info_unchecked_mode_icon_color));
			durationIcon.setVisibility(View.VISIBLE);
			infoDistanceView.setVisibility(View.VISIBLE);
			infoDurationView.setVisibility(View.VISIBLE);
		}
		if (directionInfo >= 0 && routingHelper.getRouteDirections() != null
				&& directionInfo < routingHelper.getRouteDirections().size()) {
			RouteDirectionInfo ri = routingHelper.getRouteDirections().get(directionInfo);
		} else {
			TextView distanceText = (TextView) mainView.findViewById(R.id.DistanceText);
			TextView distanceTitle = (TextView) mainView.findViewById(R.id.DistanceTitle);
			TextView durationText = (TextView) mainView.findViewById(R.id.DurationText);
			TextView durationTitle = (TextView) mainView.findViewById(R.id.DurationTitle);

			distanceText.setText(OsmAndFormatter.getFormattedDistance(ctx.getRoutingHelper().getLeftDistance(), ctx));

			durationText.setText(OsmAndFormatter.getFormattedDuration(ctx.getRoutingHelper().getLeftTime(), ctx));
			durationTitle.setText(ctx.getString(R.string.arrive_at_time, OsmAndFormatter.getFormattedTime(ctx.getRoutingHelper().getLeftTime(), true)));

			AndroidUtils.setTextPrimaryColor(ctx, distanceText, nightMode);
			AndroidUtils.setTextSecondaryColor(ctx, distanceTitle, nightMode);
			AndroidUtils.setTextPrimaryColor(ctx, durationText, nightMode);
			AndroidUtils.setTextSecondaryColor(ctx, durationTitle, nightMode);
		}

		FrameLayout detailsButton = mainView.findViewById(R.id.details_button);

		AndroidUtils.setBackground(ctx, mainView.findViewById(R.id.details_button_descr), nightMode, R.drawable.btn_border_trans_light, R.drawable.btn_border_trans_dark);
		detailsButton.setForeground(ContextCompat.getDrawable(ctx, nightMode ? R.drawable.ripple_dark : R.drawable.ripple_light));

		int color = ContextCompat.getColor(mapActivity, nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light);

		((TextView) mainView.findViewById(R.id.details_button_descr)).setTextColor(color);

		detailsButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ShowRouteInfoDialogFragment.showDialog(mapActivity.getSupportFragmentManager());
			}
		});

		buildHeader(mainView);
	}

	private void buildHeader(View headerView) {
		OsmandApplication app = mapActivity.getMyApplication();
		final LineChart mChart = (LineChart) headerView.findViewById(R.id.chart);
		GpxUiHelper.setupSimpleGPXChart(app, mChart, 4,!nightMode);

		GPXUtilities.GPXTrackAnalysis analysis = gpx.getAnalysis(0);
		if (analysis.hasElevationData) {
			List<ILineDataSet> dataSets = new ArrayList<>();
			elevationDataSet = GpxUiHelper.createGPXElevationDataSet(app, mChart, analysis,
					GpxUiHelper.GPXDataSetAxisType.DISTANCE, false, true);
			if (elevationDataSet != null) {
				dataSets.add(elevationDataSet);
			}
			slopeDataSet = GpxUiHelper.createGPXSlopeDataSet(app, mChart, analysis,
					GpxUiHelper.GPXDataSetAxisType.DISTANCE, elevationDataSet.getValues(), true, true);
			if (slopeDataSet != null) {
				dataSets.add(slopeDataSet);
			}
			LineData data = new LineData(dataSets);
			mChart.setData(data);

			mChart.setOnChartGestureListener(new OnChartGestureListener() {

				float highlightDrawX = -1;

				@Override
				public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
					if (mChart.getHighlighted() != null && mChart.getHighlighted().length > 0) {
						highlightDrawX = mChart.getHighlighted()[0].getDrawX();
					} else {
						highlightDrawX = -1;
					}
				}

				@Override
				public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
					gpxItem.chartMatrix = new Matrix(mChart.getViewPortHandler().getMatrixTouch());
					Highlight[] highlights = mChart.getHighlighted();
					if (highlights != null && highlights.length > 0) {
						gpxItem.chartHighlightPos = highlights[0].getX();
					} else {
						gpxItem.chartHighlightPos = -1;
					}
				}

				@Override
				public void onChartLongPressed(MotionEvent me) {
				}

				@Override
				public void onChartDoubleTapped(MotionEvent me) {
				}

				@Override
				public void onChartSingleTapped(MotionEvent me) {
				}

				@Override
				public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
				}

				@Override
				public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
				}

				@Override
				public void onChartTranslate(MotionEvent me, float dX, float dY) {
					if (highlightDrawX != -1) {
						Highlight h = mChart.getHighlightByTouchPoint(highlightDrawX, 0f);
						if (h != null) {
							mChart.highlightValue(h);
						}
					}
				}
			});

			mChart.setVisibility(View.VISIBLE);
		} else {
			elevationDataSet = null;
			slopeDataSet = null;
			mChart.setVisibility(View.GONE);
		}
	}

	private void updateButtonState(final OsmandApplication ctx, final List<ApplicationMode> visible,
	                               final Set<ApplicationMode> selected, final View.OnClickListener onClickListener, final View[] buttons,
	                               int i, final boolean singleChoice, final boolean useMapTheme) {
		if (buttons[i] != null) {
			View tb = buttons[i];
			final ApplicationMode mode = visible.get(i);
			final boolean checked = selected.contains(mode);
			ImageView iv = (ImageView) tb.findViewById(R.id.app_mode_icon);
			if (checked) {
				Drawable drawable = ctx.getUIUtilities().getIcon(mode.getSmallIconDark(), nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light);
				iv.setImageDrawable(drawable);
				iv.setContentDescription(String.format("%s %s", mode.toHumanString(ctx), ctx.getString(R.string.item_checked)));
				AndroidUtils.setBackground(mapActivity, iv, nightMode, R.drawable.btn_border_trans_light, R.drawable.btn_border_trans_dark);
				AndroidUtils.setForeground(mapActivity, iv, nightMode, R.drawable.ripple_light, R.drawable.ripple_dark);
			} else {
				if (useMapTheme) {
					Drawable drawable = ctx.getUIUtilities().getIcon(mode.getSmallIconDark(), nightMode ? R.color.route_info_control_icon_color_dark : R.color.route_info_control_icon_color_light);
					if (Build.VERSION.SDK_INT >= 21) {
						Drawable active = ctx.getUIUtilities().getIcon(mode.getSmallIconDark(), nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light);
						drawable = AndroidUtils.createPressedStateListDrawable(drawable, active);
					}
					iv.setImageDrawable(drawable);
					AndroidUtils.setBackground(mapActivity, iv, nightMode, R.drawable.btn_border_pressed_light, R.drawable.btn_border_pressed_dark);
					AndroidUtils.setForeground(mapActivity, iv, nightMode, R.drawable.ripple_light, R.drawable.ripple_dark);
				} else {
					iv.setImageDrawable(ctx.getUIUtilities().getThemedIcon(mode.getSmallIconDark()));
				}
				iv.setContentDescription(String.format("%s %s", mode.toHumanString(ctx), ctx.getString(R.string.item_unchecked)));
			}
			tb.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					boolean isChecked = !checked;
					if (singleChoice) {
						if (isChecked) {
							selected.clear();
							selected.add(mode);
						}
					} else {
						if (isChecked) {
							selected.add(mode);
						} else {
							selected.remove(mode);
						}
					}
					if (onClickListener != null) {
						onClickListener.onClick(null);
					}
					for (int i = 0; i < visible.size(); i++) {
						updateButtonState(ctx, visible, selected, onClickListener, buttons, i, singleChoice, useMapTheme);
					}
				}
			});
		}
	}

	private void updateViaView(final View parentView) {
		String via = generateViaDescription();
		View viaLayout = parentView.findViewById(R.id.ViaLayout);
		View viaLayoutDivider = parentView.findViewById(R.id.viaLayoutDivider);
		if (via.length() == 0) {
			viaLayout.setVisibility(View.GONE);
			viaLayoutDivider.setVisibility(View.GONE);
		} else {
			viaLayout.setVisibility(View.VISIBLE);
			viaLayoutDivider.setVisibility(View.VISIBLE);
			((TextView) parentView.findViewById(R.id.ViaView)).setText(via);
		}

		viaLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (getTargets().checkPointToNavigateShort()) {
					mapActivity.getMapActions().openIntermediatePointsDialog();
				}
			}
		});

		ImageView viaIcon = (ImageView) parentView.findViewById(R.id.viaIcon);
		viaIcon.setImageDrawable(getIconOrig(R.drawable.list_intermediate));

		FrameLayout viaButton = (FrameLayout) parentView.findViewById(R.id.via_button);

		AndroidUtils.setBackground(mapActivity, viaButton, nightMode, R.drawable.btn_border_trans_rounded_light, R.drawable.btn_border_trans_rounded_dark);
		AndroidUtils.setForeground(mapActivity, viaButton, nightMode, R.drawable.ripple_rounded_light, R.drawable.ripple_rounded_dark);

		ImageView viaButtonImageView = (ImageView) parentView.findViewById(R.id.via_button_image_view);

		Drawable normal = mapActivity.getMyApplication().getUIUtilities().getIcon(R.drawable.ic_action_edit_dark, nightMode ? R.color.route_info_control_icon_color_dark : R.color.route_info_control_icon_color_light);
		if (Build.VERSION.SDK_INT >= 21) {
			Drawable active = mapActivity.getMyApplication().getUIUtilities().getIcon(R.drawable.ic_action_edit_dark, nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light);

			normal = AndroidUtils.createPressedStateListDrawable(normal, active);
		}
		viaButtonImageView.setImageDrawable(normal);
		viaButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (getTargets().checkPointToNavigateShort()) {
					mapActivity.getMapActions().openIntermediatePointsDialog();
				}
			}
		});
	}


	private View createToggle(LayoutInflater layoutInflater, OsmandApplication ctx, LinearLayout layout, ApplicationMode mode, boolean useMapTheme) {
		int metricsX = (int) ctx.getResources().getDimension(R.dimen.route_info_modes_height);
		int metricsY = (int) ctx.getResources().getDimension(R.dimen.route_info_modes_height);
		View tb = layoutInflater.inflate(R.layout.mode_view_route_preparation, null);
		ImageView iv = (ImageView) tb.findViewById(R.id.app_mode_icon);
		iv.setImageDrawable(ctx.getUIUtilities().getIcon(mode.getSmallIconDark(), nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light));
		iv.setContentDescription(mode.toHumanString(ctx));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(metricsX, metricsY);
		layout.addView(tb, lp);
		return tb;
	}

	private void updateToSpinner(final View parentView) {
		final Spinner toSpinner = setupToSpinner(parentView);
		toSpinner.setClickable(false);
		final View toLayout = parentView.findViewById(R.id.ToLayout);
		toSpinner.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				event.offsetLocation(AndroidUtils.dpToPx(mapActivity, 48f), 0);
				toLayout.onTouchEvent(event);
				return true;
			}
		});
		toSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, final long id) {
				parentView.post(new Runnable() {
					@Override
					public void run() {
						if (id == SPINNER_FAV_ID) {
							selectFavorite(parentView, true, false);
						} else if (id == SPINNER_MAP_ID) {
							selectOnScreen(true, false);
						} else if (id == SPINNER_ADDRESS_ID) {
							mapActivity.showQuickSearch(MapActivity.ShowQuickSearchMode.DESTINATION_SELECTION, false);
							setupToSpinner(parentView);
						} else if (id == SPINNER_MAP_MARKER_MORE_ID) {
							selectMapMarker(-1, true, false);
							setupToSpinner(parentView);
						} else if (id == SPINNER_MAP_MARKER_1_ID) {
							selectMapMarker(0, true, false);
						} else if (id == SPINNER_MAP_MARKER_2_ID) {
							selectMapMarker(1, true, false);
						} else if (id == SPINNER_MAP_MARKER_3_ID) {
							selectMapMarker(2, true, false);
						}
					}
				});
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		toLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				toSpinner.performClick();
			}
		});

		final FrameLayout toButton = (FrameLayout) parentView.findViewById(R.id.to_button);

		AndroidUtils.setBackground(mapActivity, toButton, nightMode, R.drawable.btn_border_trans_rounded_light, R.drawable.btn_border_trans_rounded_dark);
		AndroidUtils.setForeground(mapActivity, toButton, nightMode, R.drawable.ripple_rounded_light, R.drawable.ripple_rounded_dark);

		ImageView toButtonImageView = (ImageView) parentView.findViewById(R.id.to_button_image_view);

		Drawable normal = mapActivity.getMyApplication().getUIUtilities().getIcon(R.drawable.ic_action_plus, nightMode ? R.color.route_info_control_icon_color_dark : R.color.route_info_control_icon_color_light);
		if (Build.VERSION.SDK_INT >= 21) {
			Drawable active = mapActivity.getMyApplication().getUIUtilities().getIcon(R.drawable.ic_action_plus, nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light);

			normal = AndroidUtils.createPressedStateListDrawable(normal, active);
		}

		toButtonImageView.setImageDrawable(normal);
		toButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (mapActivity != null) {
					final ListPopupWindow popup = new ListPopupWindow(mapActivity);
					popup.setAnchorView(toLayout);
					popup.setDropDownGravity(Gravity.END | Gravity.TOP);
					popup.setModal(true);
					popup.setAdapter(getIntermediatesPopupAdapter(mapActivity));
					popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
						@Override
						public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
							boolean hideDashboard = false;
							if (id == MapRouteInfoMenu.SPINNER_FAV_ID) {
								selectFavorite(null, false, true);
							} else if (id == MapRouteInfoMenu.SPINNER_MAP_ID) {
								hideDashboard = true;
								selectOnScreen(false, true);
							} else if (id == MapRouteInfoMenu.SPINNER_ADDRESS_ID) {
								mapActivity.showQuickSearch(MapActivity.ShowQuickSearchMode.INTERMEDIATE_SELECTION, false);
							} else if (id == MapRouteInfoMenu.SPINNER_MAP_MARKER_MORE_ID) {
								selectMapMarker(-1, false, true);
							} else if (id == MapRouteInfoMenu.SPINNER_MAP_MARKER_1_ID) {
								selectMapMarker(0, false, true);
							} else if (id == MapRouteInfoMenu.SPINNER_MAP_MARKER_2_ID) {
								selectMapMarker(1, false, true);
							}
							popup.dismiss();
							if (hideDashboard) {
								mapActivity.getDashboard().hideDashboard();
							}
						}
					});
					popup.show();
				}
			}
		});

		updateToIcon(parentView);
	}

	private void updateToIcon(View parentView) {
		ImageView toIcon = (ImageView) parentView.findViewById(R.id.toIcon);
		toIcon.setImageDrawable(getIconOrig(R.drawable.list_destination));
	}

	private void updateFromSpinner(final View parentView) {
		final TargetPointsHelper targets = getTargets();
		final Spinner fromSpinner = setupFromSpinner(parentView);
		fromSpinner.setClickable(false);
		final View fromLayout = parentView.findViewById(R.id.FromLayout);
		fromSpinner.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				event.offsetLocation(AndroidUtils.dpToPx(mapActivity, 48f), 0);
				fromLayout.onTouchEvent(event);
				return true;
			}
		});
		fromSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, final long id) {
				parentView.post(new Runnable() {
					@Override
					public void run() {
						if (id == SPINNER_MY_LOCATION_ID) {
							if (targets.getPointToStart() != null) {
								targets.clearStartPoint(true);
								mapActivity.getMyApplication().getSettings().backupPointToStart();
							}
							updateFromIcon(parentView);
						} else if (id == SPINNER_FAV_ID) {
							selectFavorite(parentView, false, false);
						} else if (id == SPINNER_MAP_ID) {
							selectOnScreen(false, false);
						} else if (id == SPINNER_ADDRESS_ID) {
							mapActivity.showQuickSearch(MapActivity.ShowQuickSearchMode.START_POINT_SELECTION, false);
							setupFromSpinner(parentView);
						} else if (id == SPINNER_MAP_MARKER_MORE_ID) {
							selectMapMarker(-1, false, false);
							setupFromSpinner(parentView);
						} else if (id == SPINNER_MAP_MARKER_1_ID) {
							selectMapMarker(0, false, false);
						} else if (id == SPINNER_MAP_MARKER_2_ID) {
							selectMapMarker(1, false, false);
						} else if (id == SPINNER_MAP_MARKER_3_ID) {
							selectMapMarker(2, false, false);
						}
					}
				});
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		fromLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				fromSpinner.performClick();
			}
		});

		FrameLayout fromButton = (FrameLayout) parentView.findViewById(R.id.from_button);

		AndroidUtils.setBackground(mapActivity, fromButton, nightMode, R.drawable.btn_border_trans_rounded_light, R.drawable.btn_border_trans_rounded_dark);
		AndroidUtils.setForeground(mapActivity, fromButton, nightMode, R.drawable.ripple_rounded_light, R.drawable.ripple_rounded_dark);

		ImageView swapDirectionView = (ImageView) parentView.findViewById(R.id.from_button_image_view);

		Drawable normal = mapActivity.getMyApplication().getUIUtilities().getIcon(R.drawable.ic_action_change_navigation_points, nightMode ? R.color.route_info_control_icon_color_dark : R.color.route_info_control_icon_color_light);
		if (Build.VERSION.SDK_INT >= 21) {
			Drawable active = mapActivity.getMyApplication().getUIUtilities().getIcon(R.drawable.ic_action_change_navigation_points, nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light);
			normal = AndroidUtils.createPressedStateListDrawable(normal, active);
		}

		swapDirectionView.setImageDrawable(normal);
		fromButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				TargetPointsHelper targetPointsHelper = getTargets();
				TargetPoint startPoint = targetPointsHelper.getPointToStart();
				TargetPoint endPoint = targetPointsHelper.getPointToNavigate();

				if (startPoint == null) {
					Location loc = mapActivity.getMyApplication().getLocationProvider().getLastKnownLocation();
					if (loc != null) {
						startPoint = TargetPoint.createStartPoint(new LatLon(loc.getLatitude(), loc.getLongitude()),
								new PointDescription(PointDescription.POINT_TYPE_MY_LOCATION,
										mapActivity.getString(R.string.shared_string_my_location)));
					}
				}

				if (startPoint != null && endPoint != null) {
					targetPointsHelper.navigateToPoint(startPoint.point, false, -1, startPoint.getPointDescription(mapActivity));
					targetPointsHelper.setStartPoint(endPoint.point, false, endPoint.getPointDescription(mapActivity));
					targetPointsHelper.updateRouteAndRefresh(true);

					updateInfo(mainView);
				}
			}
		});

		updateFromIcon(parentView);
	}

	public void updateFromIcon(View parentView) {
		((ImageView) parentView.findViewById(R.id.fromIcon)).setImageDrawable(ContextCompat.getDrawable(mapActivity,
				getTargets().getPointToStart() == null ? R.drawable.ic_action_location_color : R.drawable.list_startpoint));
	}

	public void selectOnScreen(boolean target, boolean intermediate) {
		selectFromMapTouch = true;
		selectFromMapForTarget = target;
		selectFromMapForIntermediate = intermediate;
		hide();
	}

	public void selectAddress(String name, LatLon l, final boolean target, final boolean intermediate) {
		PointDescription pd = new PointDescription(PointDescription.POINT_TYPE_ADDRESS, name);
		if (intermediate) {
			getTargets().navigateToPoint(l, true, getTargets().getIntermediatePoints().size(), pd);
		} else if (target) {
			getTargets().navigateToPoint(l, true, -1, pd);
		} else {
			getTargets().setStartPoint(l, true, pd);
		}
		updateMenu();
	}

	public void selectFavorite(@Nullable final View parentView, final boolean target, final boolean intermediate) {
		FragmentManager fragmentManager = mapActivity.getSupportFragmentManager();
		FavouritesBottomSheetMenuFragment fragment = new FavouritesBottomSheetMenuFragment();
		Bundle args = new Bundle();
		args.putBoolean(FavouritesBottomSheetMenuFragment.TARGET, target);
		args.putBoolean(FavouritesBottomSheetMenuFragment.INTERMEDIATE, intermediate);
		fragment.setArguments(args);
		fragment.show(fragmentManager, FavouritesBottomSheetMenuFragment.TAG);
	}

	public void setupSpinners(final boolean target, final boolean intermediate) {
		if (!intermediate && mainView != null) {
			if (target) {
				setupToSpinner(mainView);
			} else {
				setupFromSpinner(mainView);
			}
		}
	}

	public void selectMapMarker(final int index, final boolean target, final boolean intermediate) {
		if (index != -1) {
			MapMarker m = mapActivity.getMyApplication().getMapMarkersHelper().getMapMarkers().get(index);
			LatLon point = new LatLon(m.getLatitude(), m.getLongitude());
			if (intermediate) {
				getTargets().navigateToPoint(point, true, getTargets().getIntermediatePoints().size(), m.getPointDescription(mapActivity));
			} else if (target) {
				getTargets().navigateToPoint(point, true, -1, m.getPointDescription(mapActivity));
			} else {
				getTargets().setStartPoint(point, true, m.getPointDescription(mapActivity));
			}
			updateFromIcon();

		} else {

			MapMarkerSelectionFragment selectionFragment = MapMarkerSelectionFragment.newInstance(target, intermediate);
			selectionFragment.show(mapActivity.getSupportFragmentManager(), MapMarkerSelectionFragment.TAG);
		}
	}

	private boolean isLight() {
		return !nightMode;
	}

	private Drawable getIconOrig(int iconId) {
		UiUtilities iconsCache = mapActivity.getMyApplication().getUIUtilities();
		return iconsCache.getIcon(iconId, 0);
	}

	public static int getDirectionInfo() {
		return directionInfo;
	}

	public static boolean isVisible() {
		return visible;
	}

	public WeakReference<MapRouteInfoMenuFragment> findMenuFragment() {
		Fragment fragment = mapActivity.getSupportFragmentManager().findFragmentByTag(MapRouteInfoMenuFragment.TAG);
		if (fragment != null && !fragment.isDetached()) {
			return new WeakReference<>((MapRouteInfoMenuFragment) fragment);
		} else {
			return null;
		}
	}

	public static boolean isControlVisible() {
		return controlVisible;
	}

	public static void showLocationOnMap(MapActivity mapActivity, double latitude, double longitude) {
		RotatedTileBox tb = mapActivity.getMapView().getCurrentRotatedTileBox().copy();
		int tileBoxWidthPx = 0;
		int tileBoxHeightPx = 0;

		MapRouteInfoMenu routeInfoMenu = mapActivity.getMapLayers().getMapControlsLayer().getMapRouteInfoMenu();
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = routeInfoMenu.findMenuFragment();
		if (fragmentRef != null) {
			MapRouteInfoMenuFragment f = fragmentRef.get();
			if (mapActivity.isLandscapeLayout()) {
				tileBoxWidthPx = tb.getPixWidth() - f.getWidth();
			} else {
				tileBoxHeightPx = tb.getPixHeight() - f.getHeight();
			}
		}
		mapActivity.getMapView().fitLocationToMap(latitude, longitude, mapActivity.getMapView().getZoom(),
				tileBoxWidthPx, tileBoxHeightPx, AndroidUtils.dpToPx(mapActivity, 40f), true);
	}

	@Override
	public void newRouteIsCalculated(boolean newRoute, ValueHolder<Boolean> showToast) {
		directionInfo = -1;
		updateMenu();
		if (isVisible()) {
			showToast.value = false;
		}
	}

	public String generateViaDescription() {
		TargetPointsHelper targets = getTargets();
		List<TargetPoint> points = targets.getIntermediatePointsNavigation();
		if (points.size() == 0) {
			return "";
		}
		StringBuilder via = new StringBuilder();
		for (int i = 0; i < points.size(); i++) {
			if (i > 0) {
				via.append(" ");
			}
			TargetPoint p = points.get(i);
			String description = p.getOnlyName();
			via.append(getRoutePointDescription(p.point, description));
			boolean needAddress = new PointDescription(PointDescription.POINT_TYPE_LOCATION, description).isSearchingAddress(mapActivity)
					&& !intermediateRequestsLatLon.contains(p.point);
			if (needAddress) {
				AddressLookupRequest lookupRequest = new AddressLookupRequest(p.point, new GeocodingLookupService.OnAddressLookupResult() {
					@Override
					public void geocodingDone(String address) {
						updateMenu();
					}
				}, null);
				intermediateRequestsLatLon.add(p.point);
				geocodingLookupService.lookupAddress(lookupRequest);
			}
		}
		return via.toString();
	}

	public String getRoutePointDescription(double lat, double lon) {
		return mapActivity.getString(R.string.route_descr_lat_lon, lat, lon);
	}

	public String getRoutePointDescription(LatLon l, String d) {
		if (d != null && d.length() > 0) {
			return d.replace(':', ' ');
		}
		if (l != null) {
			return mapActivity.getString(R.string.route_descr_lat_lon, l.getLatitude(), l.getLongitude());
		}
		return "";
	}

	private Spinner setupFromSpinner(View view) {
		List<RouteSpinnerRow> fromActions = new ArrayList<>();
		fromActions.add(new RouteSpinnerRow(SPINNER_MY_LOCATION_ID, R.drawable.ic_action_get_my_location,
				mapActivity.getString(R.string.shared_string_my_location)));
		fromActions.add(new RouteSpinnerRow(SPINNER_FAV_ID, R.drawable.ic_action_fav_dark,
				mapActivity.getString(R.string.shared_string_favorite) + mapActivity.getString(R.string.shared_string_ellipsis)));
		fromActions.add(new RouteSpinnerRow(SPINNER_MAP_ID, R.drawable.ic_action_marker_dark,
				mapActivity.getString(R.string.shared_string_select_on_map)));
		fromActions.add(new RouteSpinnerRow(SPINNER_ADDRESS_ID, R.drawable.ic_action_home_dark,
				mapActivity.getString(R.string.shared_string_address) + mapActivity.getString(R.string.shared_string_ellipsis)));

		TargetPoint start = getTargets().getPointToStart();
		int startPos = -1;
		if (start != null) {
			String oname = start.getOnlyName().length() > 0 ? start.getOnlyName()
					: (mapActivity.getString(R.string.route_descr_map_location) + " " + getRoutePointDescription(start.getLatitude(), start.getLongitude()));
			startPos = fromActions.size();
			fromActions.add(new RouteSpinnerRow(SPINNER_START_ID, R.drawable.ic_action_get_my_location, oname));

			final LatLon latLon = start.point;
			final PointDescription pointDescription = start.getOriginalPointDescription();
			boolean needAddress = pointDescription != null && pointDescription.isSearchingAddress(mapActivity);
			cancelStartPointAddressRequest();
			if (needAddress) {
				startPointRequest = new AddressLookupRequest(latLon, new GeocodingLookupService.OnAddressLookupResult() {
					@Override
					public void geocodingDone(String address) {
						startPointRequest = null;
						updateMenu();
					}
				}, null);
				geocodingLookupService.lookupAddress(startPointRequest);
			}
		}

		addMarkersToSpinner(fromActions);

		final Spinner fromSpinner = ((Spinner) view.findViewById(R.id.FromSpinner));
		RouteSpinnerArrayAdapter fromAdapter = new RouteSpinnerArrayAdapter(view.getContext());
		for (RouteSpinnerRow row : fromActions) {
			fromAdapter.add(row);
		}
		fromSpinner.setAdapter(fromAdapter);
		if (start != null) {
			fromSpinner.setSelection(startPos);
		} else {
			if (mapActivity.getMyApplication().getLocationProvider().getLastKnownLocation() == null) {
				fromSpinner.setPromptId(R.string.search_poi_location);
			}
			//fromSpinner.setSelection(0);
		}
		return fromSpinner;
	}

	private Spinner setupToSpinner(View view) {
		final Spinner toSpinner = ((Spinner) view.findViewById(R.id.ToSpinner));
		final TargetPointsHelper targets = getTargets();
		List<RouteSpinnerRow> toActions = new ArrayList<>();

		TargetPoint finish = getTargets().getPointToNavigate();
		if (finish != null) {
			toActions.add(new RouteSpinnerRow(SPINNER_FINISH_ID, R.drawable.ic_action_get_my_location,
					getRoutePointDescription(targets.getPointToNavigate().point,
							targets.getPointToNavigate().getOnlyName())));

			final LatLon latLon = finish.point;
			final PointDescription pointDescription = finish.getOriginalPointDescription();
			boolean needAddress = pointDescription != null && pointDescription.isSearchingAddress(mapActivity);
			cancelTargetPointAddressRequest();
			if (needAddress) {
				targetPointRequest = new AddressLookupRequest(latLon, new GeocodingLookupService.OnAddressLookupResult() {
					@Override
					public void geocodingDone(String address) {
						targetPointRequest = null;
						updateMenu();
					}
				}, null);
				geocodingLookupService.lookupAddress(targetPointRequest);
			}

		} else {
			toSpinner.setPromptId(R.string.route_descr_select_destination);
			toActions.add(new RouteSpinnerRow(SPINNER_HINT_ID, R.drawable.ic_action_get_my_location,
					mapActivity.getString(R.string.route_descr_select_destination)));
		}
		toActions.add(new RouteSpinnerRow(SPINNER_FAV_ID, R.drawable.ic_action_fav_dark,
				mapActivity.getString(R.string.shared_string_favorite) + mapActivity.getString(R.string.shared_string_ellipsis)));
		toActions.add(new RouteSpinnerRow(SPINNER_MAP_ID, R.drawable.ic_action_marker_dark,
				mapActivity.getString(R.string.shared_string_select_on_map)));
		toActions.add(new RouteSpinnerRow(SPINNER_ADDRESS_ID, R.drawable.ic_action_home_dark,
				mapActivity.getString(R.string.shared_string_address) + mapActivity.getString(R.string.shared_string_ellipsis)));

		addMarkersToSpinner(toActions);

		RouteSpinnerArrayAdapter toAdapter = new RouteSpinnerArrayAdapter(view.getContext());
		for (RouteSpinnerRow row : toActions) {
			toAdapter.add(row);
		}
		toSpinner.setAdapter(toAdapter);
		return toSpinner;
	}

	public RoutePopupListArrayAdapter getIntermediatesPopupAdapter(Context ctx) {
		List<RouteSpinnerRow> viaActions = new ArrayList<>();

		viaActions.add(new RouteSpinnerRow(SPINNER_FAV_ID, R.drawable.ic_action_fav_dark,
				mapActivity.getString(R.string.shared_string_favorite) + mapActivity.getString(R.string.shared_string_ellipsis)));
		viaActions.add(new RouteSpinnerRow(SPINNER_MAP_ID, R.drawable.ic_action_marker_dark,
				mapActivity.getString(R.string.shared_string_select_on_map)));
		viaActions.add(new RouteSpinnerRow(SPINNER_ADDRESS_ID, R.drawable.ic_action_home_dark,
				mapActivity.getString(R.string.shared_string_address) + mapActivity.getString(R.string.shared_string_ellipsis)));

		addMarkersToSpinner(viaActions);

		RoutePopupListArrayAdapter viaAdapter = new RoutePopupListArrayAdapter(ctx);
		for (RouteSpinnerRow row : viaActions) {
			viaAdapter.add(row);
		}

		return viaAdapter;
	}

	private void addMarkersToSpinner(List<RouteSpinnerRow> actions) {
		MapMarkersHelper markersHelper = mapActivity.getMyApplication().getMapMarkersHelper();
		List<MapMarker> markers = markersHelper.getMapMarkers();
		if (markers.size() > 0) {
			MapMarker m = markers.get(0);
			actions.add(new RouteSpinnerRow(SPINNER_MAP_MARKER_1_ID,
					MapMarkerDialogHelper.getMapMarkerIcon(mapActivity.getMyApplication(), m.colorIndex),
					m.getName(mapActivity)));
		}
		if (markers.size() > 1) {
			MapMarker m = markers.get(1);
			actions.add(new RouteSpinnerRow(SPINNER_MAP_MARKER_2_ID,
					MapMarkerDialogHelper.getMapMarkerIcon(mapActivity.getMyApplication(), m.colorIndex),
					m.getName(mapActivity)));
		}
		/*
		if (markers.size() > 2) {
			MapMarker m = markers.get(2);
			actions.add(new RouteSpinnerRow(SPINNER_MAP_MARKER_3_ID,
					MapMarkerDialogHelper.getMapMarkerIcon(mapActivity.getMyApplication(), m.colorIndex),
					m.getOnlyName()));
		}
		*/
		if (markers.size() > 2) {
			actions.add(new RouteSpinnerRow(SPINNER_MAP_MARKER_MORE_ID, 0,
					mapActivity.getString(R.string.map_markers_other)));
		}
	}

	private TargetPointsHelper getTargets() {
		return app.getTargetPointsHelper();
	}

	@Override
	public void routeWasCancelled() {
		directionInfo = -1;
		// do not hide fragment (needed for use case entering Planning mode without destination)
	}

	@Override
	public void routeWasFinished() {
	}

	public void onDismiss() {
		visible = false;
		mapActivity.getMapView().setMapPositionX(0);
		mapActivity.getMapView().refreshMap();
		AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_route_land_left_margin), false);
		AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_right_widgets_panel), true);
		if (switched) {
			mapControlsLayer.switchToRouteFollowingLayout();
		}
		if (getTargets().getPointToNavigate() == null && !selectFromMapTouch) {
			mapActivity.getMapActions().stopNavigationWithoutConfirm();
		}
		if (onDismissListener != null) {
			onDismissListener.onDismiss(null);
		}
	}

	public void show() {
		if (!visible) {
			currentMenuState = getInitialMenuState();
			visible = true;
			switched = mapControlsLayer.switchToRoutePlanningLayout();
			boolean refreshMap = !switched;
			boolean portrait = AndroidUiHelper.isOrientationPortrait(mapActivity);
			if (!portrait) {
				mapActivity.getMapView().setMapPositionX(1);
				refreshMap = true;
			}

			if (refreshMap) {
				mapActivity.refreshMap();
			}

			MapRouteInfoMenuFragment.showInstance(mapActivity);

			if (!AndroidUiHelper.isXLargeDevice(mapActivity)) {
				AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_right_widgets_panel), false);
			}
			if (!portrait) {
				AndroidUiHelper.updateVisibility(mapActivity.findViewById(R.id.map_route_land_left_margin), true);
			}
		}
	}

	public void hide() {
		WeakReference<MapRouteInfoMenuFragment> fragmentRef = findMenuFragment();
		if (fragmentRef != null) {
			fragmentRef.get().dismiss();
		} else {
			visible = false;
		}
	}

	public void setShowMenu() {
		showMenu = true;
	}

	private class RouteSpinnerRow {
		long id;
		int iconId;
		Drawable icon;
		String text;

		public RouteSpinnerRow(long id) {
			this.id = id;
		}

		public RouteSpinnerRow(long id, int iconId, String text) {
			this.id = id;
			this.iconId = iconId;
			this.text = text;
		}

		public RouteSpinnerRow(long id, Drawable icon, String text) {
			this.id = id;
			this.icon = icon;
			this.text = text;
		}
	}

	private class RouteBaseArrayAdapter extends ArrayAdapter<RouteSpinnerRow> {

		RouteBaseArrayAdapter(@NonNull Context context, int resource) {
			super(context, resource);
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public long getItemId(int position) {
			RouteSpinnerRow row = getItem(position);
			return row.id;
		}

		@Override
		public boolean isEnabled(int position) {
			long id = getItemId(position);
			return id != SPINNER_HINT_ID;
		}

		View getRowItemView(int position, View convertView, ViewGroup parent) {
			TextView label = (TextView) super.getView(position, convertView, parent);
			RouteSpinnerRow row = getItem(position);
			label.setText(row.text);
			label.setTextColor(!isLight() ?
					ContextCompat.getColorStateList(mapActivity, android.R.color.primary_text_dark) : ContextCompat.getColorStateList(mapActivity, android.R.color.primary_text_light));
			return label;
		}

		View getListItemView(int position, View convertView, ViewGroup parent) {
			long id = getItemId(position);
			TextView label = (TextView) super.getDropDownView(position, convertView, parent);

			RouteSpinnerRow row = getItem(position);
			label.setText(row.text);
			if (id != SPINNER_HINT_ID) {
				Drawable icon = null;
				if (row.icon != null) {
					icon = row.icon;
				} else if (row.iconId > 0) {
					icon = mapActivity.getMyApplication().getUIUtilities().getThemedIcon(row.iconId);
				}
				label.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
				label.setCompoundDrawablePadding(AndroidUtils.dpToPx(mapActivity, 16f));
			} else {
				label.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
				label.setCompoundDrawablePadding(0);
			}

			if (id == SPINNER_MAP_MARKER_MORE_ID) {
				label.setTextColor(!mapActivity.getMyApplication().getSettings().isLightContent() ?
						mapActivity.getResources().getColor(R.color.color_dialog_buttons_dark) : mapActivity.getResources().getColor(R.color.color_dialog_buttons_light));
			} else {
				label.setTextColor(!mapActivity.getMyApplication().getSettings().isLightContent() ?
						ContextCompat.getColorStateList(mapActivity, android.R.color.primary_text_dark) : ContextCompat.getColorStateList(mapActivity, android.R.color.primary_text_light));
			}
			label.setPadding(AndroidUtils.dpToPx(mapActivity, 16f), 0, 0, 0);

			return label;
		}
	}

	private class RouteSpinnerArrayAdapter extends RouteBaseArrayAdapter {

		RouteSpinnerArrayAdapter(Context context) {
			super(context, android.R.layout.simple_spinner_item);
			setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			return getRowItemView(position, convertView, parent);
		}

		@Override
		public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
			return getListItemView(position, convertView, parent);
		}
	}

	private class RoutePopupListArrayAdapter extends RouteBaseArrayAdapter {

		RoutePopupListArrayAdapter(Context context) {
			super(context, android.R.layout.simple_spinner_dropdown_item);
		}

		@NonNull
		@Override
		public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
			return getListItemView(position, convertView, parent);
		}
	}
}
