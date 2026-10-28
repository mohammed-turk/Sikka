"""Verify the 83 supplied catalogue JSON records against Java @Pattern constraints."""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
records = json.loads((root / 'src/test/resources/park-catalogue.json').read_text())
assert len(records) == 83
checked = 0
for record in records:
    model = 'Ride' if 'category' in record else 'Place'
    source = (root / f'src/main/java/com/example/sikka/Model/{model}.java').read_text()
    for field, value in record.items():
        declaration = re.search(r'((?:\s*@[^;]+?)?)\s*private\s+[^;]+?\s+' + re.escape(field) + r'\s*(?:=[^;]*)?;', source)
        assert declaration, (model, field)
        patterns = re.findall(r'@Pattern\(\s*regexp\s*=\s*"([^"]+)"', declaration.group())
        for pattern in patterns:
            for item in value if isinstance(value, list) else [value]:
                if item is not None:
                    assert re.fullmatch(pattern, item), (record['name'], field, item, pattern)
                    checked += 1
print(f'PASS: {len(records)} catalogue records; {checked} constrained values accepted.')
