"""Check shipped UI lookups, including the dynamic inbox captions, in English."""
import json
import pathlib
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / 'inject/java/cat/narezany/margyt'


class FunLocalizationRuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_new_screens_have_english_translations(self):
        labels = {'Подписчики', 'Активность', 'Система', 'Монетизация'}
        for name in ('SettingsActivity', 'InboxMd3', 'ProfileMd3', 'ThanksSpace'):
            labels.update(re.findall(r'Local\.of\("([^"\n]*)"\)', (JAVA / (name + '.java')).read_text()))
        labels = sorted(label for label in labels if re.search('[А-Яа-яЁё]', label))
        values = ','.join(json.dumps(label, ensure_ascii=False) for label in labels)
        harness = '''package cat.narezany.margyt;
public class Check{
 public static void main(String[] args){
  Text.language=2;
  for(String label:new String[]{%s}){
   String translated=Local.of(label);
   if(translated.matches(".*[А-Яа-яЁё].*"))throw new AssertionError("Untranslated: "+label);
  }
  if(!"Activity".equals(Local.of("Активность"))||!"Edit".equals(Local.of("Изменить")))throw new AssertionError("English captions");
  Text.language=0;if(!"Активность".equals(Local.of("Активность")))throw new AssertionError("Russian preserved");
  Text.language=1;if(!"Активність".equals(Local.of("Активность")))throw new AssertionError("Ukrainian");
 }
}''' % values
        with tempfile.TemporaryDirectory() as folder:
            paths = []
            for name, content in {
                'Local.java': (JAVA / 'Local.java').read_text(),
                'Text.java': 'package cat.narezany.margyt;class Text{static int language;static int language3(){return language;}}',
                'Check.java': harness,
            }.items():
                path = pathlib.Path(folder) / name
                path.write_text(content)
                paths.append(str(path))
            result = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, *paths], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(['java', '-cp', folder, 'cat.narezany.margyt.Check'], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
