#!/usr/bin/env python3
import json
import os
from collections import defaultdict

REPORT_PATH = r'e:\PixelPlayer-master\duplicate_strings_report.json'
RES_ROOT = r'e:\PixelPlayer-master\app\src\main\res'


def choose_primary(name, files):
    """Determine which file should keep the string resource."""
    # Rule 1: feature-specific names
    if name.startswith('lyrics_'):
        return 'strings_player.xml'
    if name.startswith('artist_picker_'):
        return 'strings_library.xml'
    if name.startswith('widget_') or name.startswith('glance_'):
        return 'strings_widget.xml'
    if name.startswith('ai_playlist_'):
        return 'strings_home_screen.xml'
    if (name.startswith('crash_report_') or
            name in ('home_your_mix_title', 'home_empty_placeholder_refresh',
                     'home_empty_placeholder_subtitle', 'home_empty_placeholder_title')):
        return 'strings_screens.xml'
    if any(name.startswith(p) for p in
           ['song_info_', 'multi_selection_', 'playlist_', 'reorder_tabs_',
            'library_', 'song_picker_', 'batch_edit_']):
        return 'strings_library.xml'

    # Auth strings: prefer strings_auth.xml; if absent, prefer strings_cloud_services.xml
    if name.startswith('auth_'):
        if 'strings_auth.xml' in files:
            return 'strings_auth.xml'
        if 'strings_cloud_services.xml' in files:
            return 'strings_cloud_services.xml'
        non_batch = [f for f in files if not f.startswith('strings_presentation_batch_')]
        if non_batch:
            return non_batch[0]
        return files[0]

    # Rule 4: strings_components.xml vs another file
    if 'strings_components.xml' in files:
        other_files = [f for f in files if f != 'strings_components.xml']
        if other_files:
            if name.startswith('edit_song_'):
                return 'strings_library.xml'
            if name.startswith('equalizer_'):
                return 'strings_equalizer.xml'
            if name.startswith('widget_') or name.startswith('glance_'):
                return 'strings_widget.xml'
            if name.startswith('no_internet_dialog_') or name.startswith('offline_screen_'):
                return 'strings_cloud_services.xml'
            if name == 'home_option_dj_mashup':
                return 'strings_home_screen.xml'
            if any(name.startswith(p) for p in
                   ['song_info_', 'multi_selection_', 'playlist_', 'reorder_tabs_']):
                return 'strings_library.xml'
            return other_files[0]

    # Rule 2/3/5: batch vs regular feature; strings.xml vs feature file
    non_batch = [f for f in files if not f.startswith('strings_presentation_batch_')]
    if non_batch:
        # Prefer a feature-specific file over strings.xml / strings_components.xml
        if len(non_batch) == 1:
            return non_batch[0]
        if 'strings.xml' in non_batch:
            specific = [f for f in non_batch if f != 'strings.xml']
            if specific:
                return specific[0]
        return non_batch[0]

    return files[0]


def main():
    with open(REPORT_PATH, 'r', encoding='utf-8') as f:
        report = json.load(f)

    # Collect removals: directory -> file -> list of line numbers
    removals = defaultdict(lambda: defaultdict(list))
    stats = defaultdict(int)

    for entry in report:
        directory = entry['directory']
        for dup in entry['duplicates']:
            name = dup['name']
            occurrences = dup['occurrences']
            files = [o['file'] for o in occurrences]
            primary = choose_primary(name, files)
            for o in occurrences:
                if o['file'] != primary:
                    removals[directory][o['file']].append(o['line'])
                    stats[o['file']] += 1

    total_removed = 0
    for directory, files in removals.items():
        dir_path = os.path.join(RES_ROOT, directory)
        for filename, lines in files.items():
            file_path = os.path.join(dir_path, filename)
            if not os.path.exists(file_path):
                print(f'Warning: file not found {file_path}')
                continue
            with open(file_path, 'r', encoding='utf-8') as f:
                file_lines = f.readlines()

            # Remove lines from bottom to top to keep line numbers valid
            for line_no in sorted(set(lines), reverse=True):
                idx = line_no - 1  # convert to 0-based
                if 0 <= idx < len(file_lines):
                    removed = file_lines.pop(idx)
                    total_removed += 1
                    if not removed.strip():
                        print(f'Warning: removed empty/non-string line {line_no} in {file_path}')
                else:
                    print(f'Warning: line {line_no} out of range in {file_path}')

            with open(file_path, 'w', encoding='utf-8') as f:
                f.writelines(file_lines)
            print(f'Removed {len(lines)} entries from {file_path}')

    print(f'\nTotal duplicate entries removed: {total_removed}')
    print('Per file:')
    for filename, count in sorted(stats.items()):
        print(f'  {filename}: {count}')


if __name__ == '__main__':
    main()
