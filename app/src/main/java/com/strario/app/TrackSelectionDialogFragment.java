package com.cineflixter.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.Format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrackSelectionDialogFragment extends DialogFragment {
    
    private ExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;
    
    public static TrackSelectionDialogFragment newInstance(ExoPlayer player, DefaultTrackSelector selector) {
        TrackSelectionDialogFragment fragment = new TrackSelectionDialogFragment();
        fragment.exoPlayer = player;
        fragment.trackSelector = selector;
        return fragment;
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity(), R.style.CustomDialogTheme);
        
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.custom_track_selection_dialog, null);
        builder.setView(view);
        
        // Obtener información de pistas
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) {
            return builder.create();
        }
        
        // Configurar sección de calidad de video
        LinearLayout videoContainer = view.findViewById(R.id.video_quality_container);
        LinearLayout audioContainer = view.findViewById(R.id.audio_language_container);
        
        // Limpiar contenedores
        videoContainer.removeAllViews();
        audioContainer.removeAllViews();
        
        // Agregar opción Automático para calidad
        TextView autoQuality = createOptionTextView("Automático");
        autoQuality.setBackgroundResource(R.drawable.option_selector);
        autoQuality.setSelected(true);
        autoQuality.setOnClickListener(v -> {
            selectAutoVideo();
            dismiss();
        });
        videoContainer.addView(autoQuality);
        
        // Agregar opciones de calidad
        List<VideoQuality> videoQualities = new ArrayList<>();
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
                for (int j = 0; j < mappedTrackInfo.getTrackGroups(i).length; j++) {
                    for (int k = 0; k < mappedTrackInfo.getTrackGroups(i).get(j).length; k++) {
                        Format format = mappedTrackInfo.getTrackGroups(i).get(j).getFormat(k);
                        if (format.height > 0) {
                            String quality = format.height + "p";
                            if (format.frameRate > 30) quality += "60";
                            videoQualities.add(new VideoQuality(quality, i, j, k));
                        }
                    }
                }
            }
        }
        
        Collections.sort(videoQualities, (a, b) -> {
            int heightA = Integer.parseInt(a.name.replaceAll("[^0-9]", ""));
            int heightB = Integer.parseInt(b.name.replaceAll("[^0-9]", ""));
            return Integer.compare(heightB, heightA);
        });
        
        // Agregar opciones de calidad
        for (VideoQuality quality : videoQualities) {
            TextView qualityOption = createOptionTextView(quality.name);
            qualityOption.setBackgroundResource(R.drawable.option_selector);
            
            // Verificar si esta calidad está seleccionada
            if (isVideoTrackSelected(quality.rendererIndex, quality.groupIndex, quality.trackIndex)) {
                qualityOption.setSelected(true);
            } else {
                qualityOption.setSelected(false);
            }
            
            qualityOption.setOnClickListener(v -> {
                selectVideoTrack(quality.rendererIndex, quality.groupIndex, quality.trackIndex);
                dismiss();
            });
            videoContainer.addView(qualityOption);
        }
        
        // Agregar opciones de audio
        List<AudioLanguage> audioLanguages = new ArrayList<>();
        int spanishIndex = -1;
        int autoIndex = -1;
        
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            if (mappedTrackInfo.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                for (int j = 0; j < mappedTrackInfo.getTrackGroups(i).length; j++) {
                    for (int k = 0; k < mappedTrackInfo.getTrackGroups(i).get(j).length; k++) {
                        Format format = mappedTrackInfo.getTrackGroups(i).get(j).getFormat(k);
                        String displayName = getLanguageDisplayName(format.language);
                        audioLanguages.add(new AudioLanguage(displayName, i, j, k));
                        
                        if (displayName.equals("Español") && spanishIndex == -1) {
                            spanishIndex = audioLanguages.size() - 1;
                        }
                    }
                }
            }
        }
        
        // Seleccionar automáticamente Español si existe, si no, el primero
        if (!audioLanguages.isEmpty()) {
            int selectedIndex = spanishIndex >= 0 ? spanishIndex : 0;
            AudioLanguage selectedLanguage = audioLanguages.get(selectedIndex);
            selectAudioTrack(selectedLanguage.rendererIndex, selectedLanguage.groupIndex, selectedLanguage.trackIndex);
        }
        
        // Agregar opciones de audio
        for (AudioLanguage language : audioLanguages) {
            TextView audioOption = createOptionTextView(language.name);
            audioOption.setBackgroundResource(R.drawable.option_selector);
            
            // Verificar si este idioma de audio está seleccionado
            if (isAudioTrackSelected(language.rendererIndex, language.groupIndex, language.trackIndex)) {
                audioOption.setSelected(true);
            } else {
                audioOption.setSelected(false);
            }
            
            audioOption.setOnClickListener(v -> {
                selectAudioTrack(language.rendererIndex, language.groupIndex, language.trackIndex);
                dismiss();
            });
            audioContainer.addView(audioOption);
        }
        
        return builder.create();
    }
    
    private String getLanguageDisplayName(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) return "Español";
        
        switch (languageCode.toLowerCase()) {
            case "es": return "Español";
            case "en": return "Inglés";
            case "pt": return "Portugués";
            case "fr": return "Francés";
            case "de": return "Alemán";
            case "it": return "Italiano";
            case "ja": return "Japonés";
            case "ko": return "Coreano";
            default: return languageCode.toUpperCase();
        }
    }
    
    private TextView createOptionTextView(String text) {
        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setTextColor(getResources().getColor(android.R.color.white));
        textView.setTextSize(16);
        textView.setPadding(32, 24, 32, 24);
        textView.setBackgroundResource(R.drawable.option_selector);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 8, 0, 8);
        textView.setLayoutParams(params);
        
        return textView;
    }
    
    private boolean isVideoTrackSelected(int rendererIndex, int groupIndex, int trackIndex) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
            DefaultTrackSelector.SelectionOverride override = parameters.getSelectionOverride(rendererIndex, mappedTrackInfo.getTrackGroups(rendererIndex));
            return override != null && override.groupIndex == groupIndex && override.tracks[0] == trackIndex;
        }
        return false;
    }
    
    private boolean isAudioTrackSelected(int rendererIndex, int groupIndex, int trackIndex) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
            DefaultTrackSelector.SelectionOverride override = parameters.getSelectionOverride(rendererIndex, mappedTrackInfo.getTrackGroups(rendererIndex));
            return override != null && override.groupIndex == groupIndex && override.tracks[0] == trackIndex;
        }
        return false;
    }
    
    private void selectAutoVideo() {
        // Configurar para selección automática
        DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.buildUponParameters();
        parametersBuilder.clearSelectionOverrides(C.TRACK_TYPE_VIDEO);
        trackSelector.setParameters(parametersBuilder.build());
    }
    
    private void selectVideoTrack(int rendererIndex, int groupIndex, int trackIndex) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.buildUponParameters();
            parametersBuilder.setSelectionOverride(rendererIndex, mappedTrackInfo.getTrackGroups(rendererIndex), 
                new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex));
            trackSelector.setParameters(parametersBuilder.build());
        }
    }
    
    private void selectAudioTrack(int rendererIndex, int groupIndex, int trackIndex) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
            DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.buildUponParameters();
            parametersBuilder.setSelectionOverride(rendererIndex, mappedTrackInfo.getTrackGroups(rendererIndex), 
                new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex));
            trackSelector.setParameters(parametersBuilder.build());
        }
    }
    
    private static class VideoQuality {
        String name;
        int rendererIndex;
        int groupIndex;
        int trackIndex;
        
        VideoQuality(String name, int rendererIndex, int groupIndex, int trackIndex) {
            this.name = name;
            this.rendererIndex = rendererIndex;
            this.groupIndex = groupIndex;
            this.trackIndex = trackIndex;
        }
    }
    
    private static class AudioLanguage {
        String name;
        int rendererIndex;
        int groupIndex;
        int trackIndex;
        
        AudioLanguage(String name, int rendererIndex, int groupIndex, int trackIndex) {
            this.name = name;
            this.rendererIndex = rendererIndex;
            this.groupIndex = groupIndex;
            this.trackIndex = trackIndex;
        }
    }
}