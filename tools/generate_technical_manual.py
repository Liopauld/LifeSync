#!/usr/bin/env python3
"""Build the source-audited LifeSync program manual. Requires docs/requirements.txt.

Run from any directory: python tools/generate_program_documentation.py
Reads source and Maven reports only; never reads .env or real account JSON.
"""
from pathlib import Path
from datetime import datetime
from xml.sax.saxutils import escape
import hashlib
import json
import math
import re
import xml.etree.ElementTree as ET

import reportlab
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, Frame, PageTemplate, Paragraph, Spacer, Table, TableStyle,
    PageBreak, KeepTogether, Preformatted, Image, HRFlowable,
)
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.graphics.shapes import Drawing, Rect, Polygon, Line, String, Circle

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
ASSETS = DOCS / 'assets'
OUTPUT = ROOT / 'docs/archive/LifeSync_Technical_Manual.pdf'
JAVA = ROOT / 'src/main/java/com/lifesync/lifesync'
PAGE_W, PAGE_H = A4
MARGIN = 43
WIDTH = PAGE_W - 2 * MARGIN
PURPLE = colors.HexColor('#6D28D9')
ORANGE = colors.HexColor('#D65B16')
INK = colors.HexColor('#172033')
MUTED = colors.HexColor('#526174')
PAPER = colors.HexColor('#F5F3FA')
LINE = colors.HexColor('#DED8EB')
GREEN = colors.HexColor('#047857')

fontdir = Path(reportlab.__file__).parent / 'fonts'
for family, filename in [('Vera', 'Vera.ttf'), ('Vera-Bold', 'VeraBd.ttf'), ('Vera-Italic', 'VeraIt.ttf')]:
    pdfmetrics.registerFont(TTFont(family, str(fontdir / filename)))
pdfmetrics.registerFontFamily('Vera', normal='Vera', bold='Vera-Bold', italic='Vera-Italic', boldItalic='Vera-Bold')

STYLES = {
    'body': ParagraphStyle('Body', fontName='Vera', fontSize=8.8, leading=13.1,
                           textColor=INK, spaceAfter=7),
    'small': ParagraphStyle('Small', fontName='Vera', fontSize=7.2, leading=10.5,
                            textColor=MUTED, spaceAfter=5),
    'h1': ParagraphStyle('H1', fontName='Vera-Bold', fontSize=19, leading=23,
                         textColor=PURPLE, spaceBefore=4, spaceAfter=13, keepWithNext=True),
    'h2': ParagraphStyle('H2', fontName='Vera-Bold', fontSize=11.5, leading=16,
                         textColor=INK, spaceBefore=12, spaceAfter=6, keepWithNext=True),
    'title': ParagraphStyle('Title', fontName='Vera-Bold', fontSize=38, leading=45,
                            textColor=PURPLE, spaceAfter=14),
    'subtitle': ParagraphStyle('Subtitle', fontName='Vera', fontSize=17, leading=23,
                               textColor=INK, spaceAfter=12),
    'code': ParagraphStyle('Code', fontName='Courier', fontSize=7.3, leading=10.4,
                           textColor=INK, backColor=PAPER, borderPadding=10, spaceAfter=12),
    'cell': ParagraphStyle('Cell', fontName='Vera', fontSize=7.2, leading=10.5,
                           textColor=INK),
    'headcell': ParagraphStyle('HeadCell', fontName='Vera-Bold', fontSize=7.3, leading=10.5,
                               textColor=colors.white),
    'caption': ParagraphStyle('Caption', fontName='Vera-Italic', fontSize=7.2, leading=10.5,
                              textColor=MUTED, spaceBefore=5, spaceAfter=10),
}


def p(text, style='body'):
    return Paragraph(text, STYLES[style])


def source(relative, symbol=None):
    path = JAVA / relative
    number = 1
    if symbol:
        for i, line in enumerate(path.read_text().splitlines(), 1):
            if symbol in line:
                number = i
                break
    return f'{relative}:{number}'


def tests():
    rows = []
    for path in sorted((ROOT / 'target/surefire-reports').glob('TEST-*.xml')):
        r = ET.parse(path).getroot()
        rows.append({
            'suite': r.attrib['name'].split('.')[-1],
            'tests': int(r.attrib.get('tests', 0)),
            'failures': int(r.attrib.get('failures', 0)),
            'errors': int(r.attrib.get('errors', 0)),
            'skipped': int(r.attrib.get('skipped', 0)),
            'seconds': float(r.attrib.get('time', 0)),
            'cases': [case.attrib['name'] for case in r.findall('testcase')],
        })
    if not rows:
        raise RuntimeError('Run the Maven test suite before generating this manual.')
    if any(r['failures'] or r['errors'] for r in rows):
        raise RuntimeError('The manual must not report a successful build when tests fail.')
    return rows


REQUIREMENTS = [
    ('J01', 'Classes and Objects', 'Covered',
     'User, NutritionEntry, PantryItem, Recipe, Activity, ActivityTime and RecipeMatch represent the proposal domain. '
     'Food records are NutritionEntry; ingredients are PantryItem and recipe ingredient strings; local calendar events are Activity.',
     [source('model/User.java', 'public class User'), source('model/Activity.java', 'public class Activity'), source('service/RecipeService.java', 'class RecipeMatch')]),
    ('J02', 'Arrays / ArrayLists', 'Covered',
     'ArrayList collections hold persisted records, recipes and chat messages. Arrays provide nutrient rows, food emojis and day names.',
     [source('service/DataStore.java', 'new ArrayList'), source('controller/ChatController.java', 'persistentMessages')]),
    ('J03', 'Strings', 'Covered',
     'Names, dietary preferences, ISO dates, HH:mm times, statuses, ingredient keywords and AI text are processed as strings.',
     [source('model/Recipe.java', 'private String name'), source('service/RecipeService.java', 'List<Recipe> search')]),
    ('J04', 'Methods', 'Covered',
     'Reusable methods own calculations, validation, searching, sorting, authentication, CRUD and scheduling.',
     [source('service/UserService.java', 'double calculateBMI(double'), source('service/ActivityService.java', 'findConflicts')]),
    ('J05', 'Conditionals', 'Covered',
     'If/switch branches validate inputs, classify BMI, select activity multipliers, route themes and reject conflicts.',
     [source('service/UserService.java', 'BMICategory getBMICategory'), source('model/ActivityTime.java', 'private static int minutes')]),
    ('J06', 'Loops', 'Covered',
     'Loops process entities, compare schedule intervals, expand cascade deletion, and aggregate the nutrition matrix with nested loops.',
     [source('controller/HomeController.java', 'double[][] nutrition'), source('service/ActivityService.java', 'boolean changed')]),
    ('J07', 'Searching', 'Covered',
     'Case-insensitive recipe name/ingredient/tag searches, category filtering, ID lookups and pantry matching are implemented.',
     [source('service/RecipeService.java', 'List<Recipe> search'), source('service/RecipeService.java', 'matchWithPantry')]),
    ('J08', 'Sorting', 'Covered',
     'Recipes sort by name, popularity, prep time or calories; activities sort by date/time; pantry items by category/name.',
     [source('service/RecipeService.java', 'List<Recipe> filterAndSort'), source('service/ActivityService.java', 'List<Activity> getAllActivities')]),
    ('J09', 'Multidimensional Data', 'Completed in this audit',
     'getDailyNutritionMatrix returns double[n][4]: one food-entry row and kcal/protein/carbs/fat columns. '
     'HomeController aggregates the matrix from one file snapshot. Chat context also uses List<String[]>.',
     [source('service/NutritionService.java', 'double[][] getDailyNutritionMatrix'), source('controller/HomeController.java', 'double[][] nutrition')]),
    ('J10', 'Encapsulation', 'Covered',
     'Model fields are private with controlled accessors. Services own persistence and business operations; controllers own UI behavior.',
     [source('model/User.java', 'private String id'), source('model/Recipe.java', 'public String getId')]),
    ('J11', 'Exception Handling', 'Covered',
     'Validation blocks invalid input; storage failures propagate instead of false success; AI failures return local fallback results.',
     [source('service/DataStore.java', 'UncheckedIOException'), source('service/GeminiService.java', '.exceptionally')]),
    ('J12', 'File Handling', 'Covered',
     'Gson loads/saves JSON using UTF-8 and temporary-file replacement. Theme selection is persisted. Generated favorites now enter the catalog.',
     [source('service/DataStore.java', 'void saveAll'), source('service/RecipeService.java', 'boolean toggleFavorite(Recipe')]),
    ('J13', 'API / External Integration', 'Covered through Gemini; Calendar excluded',
     'Java HttpClient integrates the existing Gemini generateContent service asynchronously. No Google Calendar API, OAuth, sync or calendar credentials are added.',
     [source('service/GeminiService.java', 'HttpClient HTTP_CLIENT'), source('service/GeminiService.java', 'callGeminiAsync')]),
]


class Manual(BaseDocTemplate):
    def __init__(self, filename):
        super().__init__(str(filename), pagesize=A4, leftMargin=MARGIN, rightMargin=MARGIN,
                         topMargin=48, bottomMargin=44, title='LifeSync Program Documentation',
                         author='LifeSync Project', subject='Source-audited program documentation and requirements coverage')
        self.addPageTemplates(PageTemplate('manual', [Frame(MARGIN, 44, WIDTH, PAGE_H - 92,
                                                          leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)],
                                          onPage=self.page_decorations))
        self.heading_index = 0

    def page_decorations(self, canv, doc):
        canv.saveState()
        if doc.page > 1:
            canv.setFont('Vera', 7)
            canv.setFillColor(MUTED)
            canv.drawString(MARGIN, PAGE_H - 24, 'LifeSync | Program documentation')
            canv.drawRightString(PAGE_W - MARGIN, PAGE_H - 24, 'Java 21 / JavaFX 21')
            canv.setStrokeColor(LINE)
            canv.line(MARGIN, PAGE_H - 32, PAGE_W - MARGIN, PAGE_H - 32)
        canv.setFont('Vera', 6.8)
        canv.setFillColor(MUTED)
        canv.drawString(MARGIN, 23, 'Source-audited edition | 24 October 2026 | Local calendar only')
        canv.drawRightString(PAGE_W - MARGIN, 23, str(doc.page))
        canv.restoreState()

    def afterFlowable(self, flowable):
        if isinstance(flowable, Paragraph) and flowable.style.name in ('H1', 'H2'):
            level = 0 if flowable.style.name == 'H1' else 1
            key = 'heading-' + str(self.heading_index)
            self.heading_index += 1
            text = flowable.getPlainText()
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(text, key, level=level, closed=level == 0)
            if level == 0:
                self.notify('TOCEntry', (level, text, self.page, key))

    def beforeDocument(self):
        self.heading_index = 0


STORY = []


def text(value, style='body'):
    STORY.append(p(value, style))


def heading(value, level=1, new_page=False):
    if new_page:
        STORY.append(PageBreak())
    text(escape(value), 'h1' if level == 1 else 'h2')


def table(headers, rows, widths=None):
    widths = widths or [WIDTH / len(headers)] * len(headers)
    data = [[p(escape(str(c)), 'headcell') for c in headers]]
    data += [[p(escape(str(c)).replace('\n', '<br/>'), 'cell') for c in row] for row in rows]
    t = Table(data, colWidths=widths, repeatRows=1, hAlign='LEFT')
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), PURPLE), ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, PAPER]),
        ('LEFTPADDING', (0, 0), (-1, -1), 8), ('RIGHTPADDING', (0, 0), (-1, -1), 8),
        ('TOPPADDING', (0, 0), (-1, -1), 7), ('BOTTOMPADDING', (0, 0), (-1, -1), 7),
        ('LINEBELOW', (0, 0), (-1, 0), .5, PURPLE),
        ('LINEBELOW', (0, 1), (-1, -1), .3, LINE),
    ]))
    STORY.extend([t, Spacer(1, 9)])


def code(value):
    lines = value.strip('\n').splitlines()
    if any(len(line) > 101 for line in lines):
        raise ValueError('A pseudocode line exceeds the printable code width.')
    STORY.append(KeepTogether([Preformatted('\n'.join(lines), STYLES['code'])]))


def screenshot(name, caption, max_height=285):
    path = ASSETS / name
    if not path.exists():
        return
    image = Image(str(path))
    scale = min(WIDTH / image.imageWidth, max_height / image.imageHeight)
    image.drawWidth, image.drawHeight = image.imageWidth * scale, image.imageHeight * scale
    STORY.append(KeepTogether([image, p(escape(caption), 'caption')]))


def wrap_words(value, max_width, font='Vera', size=8):
    result = []
    for paragraph in value.split('\n'):
        line = ''
        for word in paragraph.split():
            candidate = (line + ' ' + word).strip()
            if line and pdfmetrics.stringWidth(candidate, font, size) > max_width:
                result.append(line)
                line = word
            else:
                line = candidate
        result.append(line)
    return result


def diagram(nodes, edges, height, caption):
    """Vector flowchart. Node coordinates use a top-left origin in a 500 pt canvas."""
    d = Drawing(500, height)
    index = {node[0]: node for node in nodes}
    for edge in edges:
        start, end, label, points = edge
        a, b = index[start], index[end]
        if points is None:
            points = [(a[1] + a[3] / 2, a[2] + a[4]), (b[1] + b[3] / 2, b[2])]
        coords = [(x, height - y) for x, y in points]
        for (x1, y1), (x2, y2) in zip(coords, coords[1:]):
            d.add(Line(x1, y1, x2, y2, strokeColor=MUTED, strokeWidth=1))
        x1, y1 = coords[-2]
        x2, y2 = coords[-1]
        angle = math.atan2(y2 - y1, x2 - x1)
        back = (x2 - 6 * math.cos(angle), y2 - 6 * math.sin(angle))
        d.add(Polygon([x2, y2, back[0] + 2.7 * math.sin(angle), back[1] - 2.7 * math.cos(angle),
                       back[0] - 2.7 * math.sin(angle), back[1] + 2.7 * math.cos(angle)],
                      fillColor=MUTED, strokeColor=MUTED))
        if label:
            x, y = coords[len(coords) // 2]
            d.add(String(x + 5, y + 5, label, fontName='Vera', fontSize=7, fillColor=MUTED))
    for key, x, top, w, h, kind, label in nodes:
        y = height - top - h
        fill = {'start': '#E3F5ED', 'decision': '#FFF1DD', 'store': '#E6F2FC',
                'process': '#F0EAFB', 'external': '#F9E8E9'}.get(kind, '#F0EAFB')
        if kind == 'decision':
            d.add(Polygon([x + w / 2, y + h, x + w, y + h / 2, x + w / 2, y, x, y + h / 2],
                          fillColor=colors.HexColor(fill), strokeColor=ORANGE, strokeWidth=.8))
        else:
            d.add(Rect(x, y, w, h, rx=12 if kind == 'start' else 5,
                       fillColor=colors.HexColor(fill), strokeColor=LINE, strokeWidth=.8))
        lines = wrap_words(label, w - (w * .3 if kind == 'decision' else 15), size=7.5)
        baseline = y + h / 2 + (len(lines) - 1) * 5 - 2.5
        for i, line in enumerate(lines):
            d.add(String(x + w / 2, baseline - i * 10, line, textAnchor='middle',
                         fontName='Vera', fontSize=7.5, fillColor=INK))
    STORY.append(KeepTogether([d, p(escape(caption), 'caption')]))


def build():
    suites = tests()
    total = sum(s['tests'] for s in suites)
    snapshot_files = [ROOT / 'pom.xml'] + sorted((ROOT / 'src').rglob('*.java')) + sorted((ROOT / 'src').rglob('*.fxml')) + sorted((ROOT / 'src').rglob('*.css'))
    digest = hashlib.sha256()
    for path in snapshot_files:
        digest.update(str(path.relative_to(ROOT)).encode())
        digest.update(path.read_bytes())
    snapshot = digest.hexdigest()
    DOCS.mkdir(exist_ok=True)
    (DOCS / 'requirements_traceability.json').write_text(json.dumps([
        {'id': rid, 'requirement': requirement, 'status': status, 'evidence': evidence, 'sources': refs}
        for rid, requirement, status, evidence, refs in REQUIREMENTS
    ], indent=2), encoding='utf-8')
    (DOCS / 'verification_summary.json').write_text(json.dumps({
        'date': '2026-10-24', 'tests': total, 'failures': 0, 'errors': 0,
        'source_snapshot_sha256': snapshot, 'suites': suites,
        'google_calendar': 'Explicitly excluded; no integration implemented',
        'reference_images': {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
                             for name in ['Unknown.jpeg', 'Unknown-2.jpeg'] if (ROOT / name).exists()},
    }, indent=2), encoding='utf-8')

    # Cover
    STORY.append(Spacer(1, 58))
    text('LifeSync', 'title')
    text('Program Documentation', 'subtitle')
    text('A source-audited technical and user manual for the desktop wellness, nutrition, fitness and planning application.')
    STORY.append(Spacer(1, 22))
    cover = Table([[p('<b>Java 21 / JavaFX 21</b><br/>Local JSON persistence<br/>Optional Gemini AI<br/>Light and dark themes'),
                    Image(str(ROOT / 'src/main/resources/com/lifesync/lifesync/images/login.png'), width=93, height=144)]],
                  colWidths=[WIDTH - 125, 125])
    cover.setStyle(TableStyle([('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
                               ('BACKGROUND', (0, 0), (-1, -1), PAPER),
                               ('TOPPADDING', (0, 0), (-1, -1), 18),
                               ('BOTTOMPADDING', (0, 0), (-1, -1), 18),
                               ('LEFTPADDING', (0, 0), (-1, -1), 18)]))
    STORY.extend([cover, Spacer(1, 28)])
    text('<b>Edition:</b> 1.0 - implemented-system manual<br/><b>Date:</b> 24 October 2026<br/>'
         f'<b>Verification:</b> {total} automated tests; zero failures and errors<br/>'
         '<b>Scope decision:</b> Google Calendar integration is excluded.')
    text('Prepared from the current repository and the two supplied proposal screenshots. '
         'Illustrations use synthetic demonstration accounts. No API key, recovery code, password hash, or real account record is reproduced.', 'small')

    STORY.append(PageBreak())
    text('Contents', 'title')
    toc = TableOfContents()
    toc.levelStyles = [ParagraphStyle('TOC0', fontName='Vera-Bold', fontSize=9.5, leading=15, textColor=PURPLE, spaceBefore=7),
                       ParagraphStyle('TOC1', fontName='Vera', fontSize=8, leading=12, leftIndent=15, textColor=INK)]
    STORY.append(toc)
    text('The clickable contents list the chapters. PDF bookmarks provide direct access to individual subsections.', 'small')

    heading('1. Document control and scope', new_page=True)
    table(['Item', 'Recorded value'], [
        ('Document owner', 'LifeSync project maintainers; no individual authorship or institutional approval is assumed.'),
        ('Application version', 'Maven artifact com.LifeSync:LifeSync:1.0-SNAPSHOT'),
        ('Reference inputs', 'Unknown.jpeg: Java programming requirements; Unknown-2.jpeg: SDLC stages.'),
        ('Implementation baseline', 'Current src/, pom.xml, Maven test reports, and inspected JavaFX workflows.'),
        ('Source snapshot', snapshot),
        ('Excluded integration', 'Google Calendar: no OAuth, calendar sync, external event writes, or calendar reminders.'),
    ], [130, WIDTH - 130])
    text('This manual describes the implemented application, its development evidence and its operating procedures. '
         'It distinguishes an implemented service from a visible UI control and distinguishes automated test results from desktop inspection. '
         'The proposal class names are conceptual examples, not an instruction to create duplicate entity classes.')
    heading('1.1 Changes made after inspecting the JPEGs', 2)
    table(['Gap', 'Delivered change', 'Evidence'], [
        ('Explicit multidimensional nutrition data', 'An entry-by-nutrient double[n][4] matrix now powers the Home nutrition summary from one file read.',
         source('service/NutritionService.java', 'double[][] getDailyNutritionMatrix')),
        ('Generated recipe favorites were not retained', 'The displayed-recipe favorite overload inserts a newly generated recipe into the persisted catalog before toggling it.',
         source('service/RecipeService.java', 'boolean toggleFavorite(Recipe')),
        ('SDLC evidence was not assembled into a full manual', 'Requirements traceability, design, algorithms, flowcharts, test evidence, deployment and maintenance are recorded here.', 'Sections 2-14; docs/verification_summary.json'),
    ], [110, 240, WIDTH - 350])
    text('No Google Calendar implementation was added. The existing Gemini integration remains the external API example for requirement J13.')

    heading('2. Purpose, users and requirements', new_page=True)
    heading('2.1 Problem statement and objectives', 2)
    text('Wellness records are often split between food notes, pantry lists, workout logs and daily plans. LifeSync combines these concerns in one desktop workspace. '
         'The program helps a local user maintain a profile, review estimated nutrition, organize ingredients and recipes, and schedule activities without overlapping peer events.')
    text('The objectives are to keep the core workflows usable offline, provide optional AI assistance without blocking the JavaFX thread, persist records reliably, '
         'make information readable in two themes, and demonstrate the Java programming fundamentals in the supplied academic checklist.')
    table(['Actor / responsibility', 'Role in the system'], [
        ('Local user', 'Registers/signs in, edits profile, manages pantry and favorites, logs nutrition, schedules or completes activities, and uses chat.'),
        ('Maintainer', 'Builds with Java 21, manages dependencies, reviews changes, tests, backups and release artifacts.'),
        ('Filesystem', 'Stores per-entity JSON records and installation-wide theme/catalog state.'),
        ('Gemini service (optional)', 'Accepts selected user context and returns text or structured recipe/plan suggestions over HTTPS.'),
    ], [145, WIDTH - 145])
    heading('2.2 Functional requirements', 2)
    table(['ID', 'Observable behavior'], [
        ('F01', 'Create a local account and authenticate by username or email; maintain an in-memory active user.'),
        ('F02', 'Issue and consume hashed single-use recovery codes; reset a password only with a valid saved code.'),
        ('F03', 'Edit body metrics, preferences and profile; calculate BMI, BMR, TDEE and the application calorie target.'),
        ('F04', 'Create/edit/delete pantry ingredients, quantities and low-stock thresholds; filter by category.'),
        ('F05', 'Browse/search catalog records; match pantry ingredients; generate local or AI recipes; retain favorites.'),
        ('F06', 'Log a recipe as eaten; aggregate calories, protein, carbohydrates and fat for the selected account/day.'),
        ('F07', 'Create/edit/reschedule/delete activities; show day/week/month views, statuses and nested sub-plans.'),
        ('F08', 'Support 00:00-23:59 starts and 24:00 end-of-day boundaries; reject invalid or conflicting same-level intervals.'),
        ('F09', 'Maintain chat across tab navigation without sharing its history with another account; import valid plans as a checked batch.'),
        ('F10', 'Persist supported records and theme selection; render all existing views in light and dark modes.'),
    ], [45, WIDTH - 45])
    heading('2.3 Non-functional requirements and constraints', 2)
    text('The implementation uses Java 21, JavaFX 21, Maven Wrapper and Gson. Controllers update UI controls on the JavaFX Application Thread; '
         'network work uses CompletableFuture/HttpClient and returns through Platform.runLater. JSON storage is intended for a single local application instance, '
         'not a multi-user server. Test storage is isolated under target/test-data or JUnit temporary directories. '
         'Readable category colors, wrapping advice text, keyboard focus states and accessible control labels support usability; no formal accessibility certification is claimed.')

    heading('3. Java requirement traceability', new_page=True)
    text('The following rows transcribe the programming topics visible in Unknown.jpeg. Each is mapped to actual classes and methods. '
         'Paths are relative to src/main/java/com/lifesync/lifesync/. Line references identify the source snapshot used for this edition.')
    for rid, requirement, status, evidence, refs in REQUIREMENTS:
        heading(f'{rid}. {requirement}', 2)
        text(f'<b>Status:</b> {escape(status)}. {escape(evidence)}')
        text('<b>Source:</b> ' + escape('; '.join(refs)), 'small')
    text('Favorite and Recommendation need not be standalone entity classes: favorites are a field on Recipe, while recommendations are service results. '
         'The local Activity model represents the calendar-event concept. This keeps the implementation smaller without losing the behaviors in the screenshot.')

    heading('4. Software Development Life Cycle evidence', new_page=True)
    text('Unknown-2.jpeg specifies seven SDLC stages. The table records the activities and concrete artifacts available for this project. '
         'The documentation does not invent a historical sprint schedule, named team assignments, user-study results or a production deployment.')
    table(['Stage', 'LifeSync evidence and handoff'], [
        ('1. Planning', 'Purpose, local-user scope, optional AI, Java/JavaFX/JSON stack and explicit integration exclusion are recorded in Sections 1-2. '
         'Roles are identified by responsibility; individual assignments are not supplied.'),
        ('2. Requirements analysis', 'The two supplied images, J01-J13 traceability, F01-F10 functional requirements and constraints define acceptance. '
         'Google Calendar is removed from the original proposal scope by the user instruction.'),
        ('3. System design', 'Layered architecture, data model, storage contract, UI navigation and algorithms are recorded in Sections 5-9.'),
        ('4. Development / implementation', 'Source files implement authentication, pantry/recipe management, nutrition, activity scheduling, themes and AI. '
         'This audit added the nutrition matrix and generated-recipe favorite persistence.'),
        ('5. Testing', f'{total} current JUnit tests report zero failures/errors. Desktop checks cover themes, nutrition output, modal binding and favorite persistence. '
         'Prior regression cases are summarized separately from unit tests in Section 11.'),
        ('6. Deployment', 'Maven Wrapper and run.sh provide repeatable local execution. Java 21 setup, environment variables and packaging steps are in Section 12. '
         'A signed installer and hosted production deployment are not delivered.'),
        ('7. Maintenance / evaluation', 'Backups, troubleshooting, regression gates, source traceability and limitations are documented in Sections 13-14. '
         'Future work requires a defined requirement and renewed tests.'),
    ], [115, WIDTH - 115])
    heading('4.1 Completion and change-control rules', 2)
    text('A change is complete when the affected behavior works through its real entry point, its data survives the required reload, the existing Maven suite passes, '
         'and the UI remains readable in both themes at the supported window sizes. A requirement change must update the traceability record, algorithms and tests. '
         'New external-calendar scope is not inferred from the original proposal.')
    table(['Risk', 'Existing control / boundary'], [
        ('Unavailable or invalid AI credentials', 'Local recipe/advice fallbacks; requests have connection and response timeouts.'),
        ('Interrupted JSON save', 'Temporary write followed by atomic replacement where the filesystem supports it; backups remain necessary.'),
        ('Schedule conflicts and partial imports', 'Half-open interval checks and all-or-nothing generated-plan persistence.'),
        ('UI clipping / theme regressions', 'Wrapping/minimum preferred heights, readable semantic colors, desktop checks and the existing FXML validation test.'),
        ('Incorrect assumption about proposal implementation', 'Source references and an explicit supported/limited/excluded distinction in this manual.'),
    ], [155, WIDTH - 155])

    heading('5. System architecture and design', new_page=True)
    heading('5.1 Layer responsibilities', 2)
    text('LifeSync uses a JavaFX controller/service/model structure rather than a web architecture. FXML declares the existing UI hierarchy; CSS supplies appearance. '
         'SceneManager swaps authentication scenes and loads tab content into the main shell. Controllers handle events and display validation. '
         'Services own calculations, entity operations and external requests. Gson persists the model classes through DataStore.')
    diagram([
        ('user', 190, 0, 120, 30, 'start', 'Local desktop user'),
        ('views', 65, 65, 370, 45, 'process', 'FXML views + light/dark CSS + SceneManager'),
        ('ctrl', 65, 145, 370, 45, 'process', 'Login / Profile / Home / Plan / Meals / Pantry / Activity / Chat controllers'),
        ('domain', 20, 230, 220, 50, 'process', 'Auth, User, Nutrition, Activity, Pantry and Recipe services'),
        ('ai', 290, 230, 185, 50, 'process', 'GeminiService + scope validation'),
        ('store', 20, 320, 220, 45, 'store', 'DataStore + Gson -> local data/*.json'),
        ('remote', 290, 320, 185, 45, 'external', 'Optional Gemini HTTPS API'),
    ], [('user', 'views', '', None), ('views', 'ctrl', '', None), ('ctrl', 'domain', '', None),
        ('ctrl', 'ai', '', None), ('domain', 'store', '', None), ('ai', 'remote', '', None)],
        390, 'Figure 1. Component architecture. Calendar views are local; no Google Calendar component exists.')
    heading('5.2 Navigation and module boundaries', 2)
    text('Launcher starts LifeSyncApplication, which initializes SceneManager and shows Login. Successful authentication opens MainController. '
         'The main shell provides Home, Plan, Meals, Pantry, Activity and Chatbot navigation; the profile area opens My Profile. '
         'module-info.java requires javafx.controls, javafx.fxml, Gson, Ikonli, java.net.http and java.xml, and opens controller/model packages for FXML/Gson reflection.')
    heading('5.3 Concurrency', 2)
    text('File-backed UI actions run synchronously in the current desktop design. HTTP requests run asynchronously. Their completion callbacks schedule UI work through Platform.runLater. '
         'Chat tracks account ownership, a conversation generation and pending-response state so tab switches can restore the conversation and stale replies do not cross account/clear-chat boundaries. '
         'There is no cross-process file lock or general database transaction engine.')

    heading('6. Data model and storage dictionary', new_page=True)
    diagram([
        ('user', 160, 0, 180, 70, 'process', 'User\nid, identity, credential hashes\nbody metrics, preferences, target'),
        ('act', 0, 125, 155, 85, 'process', 'Activity\nid, userId, optional parentId\ndate, start/end, type, status\nself-referencing hierarchy'),
        ('nut', 172, 125, 155, 85, 'process', 'NutritionEntry\nid, userId, date, mealType\nfoodName, kcal, protein, carbs, fat'),
        ('pan', 344, 125, 155, 85, 'process', 'PantryItem\nid, userId, name, category\nquantity, unit, lowThreshold'),
        ('recipe', 100, 265, 300, 65, 'store', 'Shared Recipe catalog\nid, ingredients[], tags[], instructions, nutrition\nfavorite flag, times, meal type, popularity'),
    ], [('user', 'act', '1 to many', None), ('user', 'nut', '1 to many', None),
        ('user', 'pan', '1 to many', None)], 350,
        'Figure 2. Main data relationships. Recipe has no userId: catalog favorite flags are currently shared by this installation.')
    text('Identifiers are UUID strings. Dates are ISO yyyy-MM-dd strings. Activity times remain HH:mm strings in JSON, including the explicit end boundary 24:00. '
         'ActivityTime is a transient validated value object; the new nutrition matrix is also transient and does not alter the stored JSON schema.')
    table(['File', 'Contents / ownership'], [
        ('data/users.json', 'List<User>; identity, SHA-256 password hash, optional recovery-code hash and profile metrics.'),
        ('data/activities.json', 'List<Activity>; each record has userId and may reference a parent activity.'),
        ('data/pantry.json', 'List<PantryItem>; each record belongs to one userId.'),
        ('data/nutrition.json', 'List<NutritionEntry>; each record belongs to one userId and one date.'),
        ('data/recipes.json', 'Shared List<Recipe>; generated favorites are inserted here and favorite flags persist.'),
        ('data/theme.json', 'Installation-level darkMode preference, handled by ThemeManager.'),
        ('In-memory only', 'Active authentication session, chat history, AI response text, nutrition matrices and computed presentation state.'),
    ], [135, WIDTH - 135])
    for model in ['User', 'Activity', 'PantryItem', 'NutritionEntry', 'Recipe']:
        heading(f'6.{["User", "Activity", "PantryItem", "NutritionEntry", "Recipe"].index(model) + 1} {model} fields', 2)
        source_text = (JAVA / 'model' / (model + '.java')).read_text()
        fields = re.findall(r'^\s*private\s+([\w<>.]+)\s+(\w+)\s*;\s*(?://\s*(.*))?', source_text, re.M)
        table(['Field', 'Java type', 'Meaning'], [(name, typ, comment or 'See model accessors and constructor.') for typ, name, comment in fields], [110, 100, WIDTH - 210])
    heading('6.6 Representative synthetic JSON', 2)
    text('The following record is illustrative. It is not copied from real account data. Omitting parentId represents a legacy/top-level activity and deserializes as null.')
    code('''{
  "id": "example-child-id", "userId": "example-user-id",
  "parentId": "example-parent-id", "title": "Evening reading",
  "type": "STUDY", "date": "2026-10-24",
  "startTime": "23:00", "endTime": "24:00",
  "status": "UPCOMING", "caloriesBurned": 0
}''')

    heading('7. Algorithms and pseudocode', new_page=True)
    heading('7.1 Account registration, login and recovery', 2)
    text('Registration validates visible form fields, checks duplicate username/email case-insensitively, hashes the password and persists a User. '
         'Login compares the submitted password hash with the stored hash and sets the in-memory current user only on success. '
         'Recovery requires a previously saved high-entropy code; knowing an email address alone does not authorize a reset.')
    code('''REGISTER(form):
  validate required identity fields and finite body metrics
  LOAD users
  IF username OR email already exists (case-insensitive): reject
  user.passwordHash = SHA256(form.password)
  calculate daily target; SAVE user
  issue recovery code after verifying the new credentials
  show the code once; return to Login

LOGIN(identifier, password):
  LOAD users
  FIND a user matching username OR email
  IF found AND SHA256(password) equals stored passwordHash:
    currentUser = user; open dashboard
  ELSE show authentication error

RESET(identifier, code, newPassword):
  validate newPassword length and non-blank value
  FIND user matching identifier
  IF recoveryCodeHash is absent OR does not match SHA256(trim(code)): reject
  replace passwordHash; remove recoveryCodeHash
  SAVE all users; clear that user's active session
  tell user to sign in and generate a replacement recovery code''')
    text('A new recovery code contains 32 SecureRandom bytes encoded with URL-safe Base64. Only its SHA-256 hash is saved. '
         'Issuing a replacement code invalidates the previous one; successful reset consumes the code.')

    heading('7.2 BMI, BMR, TDEE and target computation', 2)
    table(['Quantity', 'Implementation rule'], [
        ('BMI', 'weightKg / (heightCm / 100)^2; rounded to one decimal. Non-positive/missing inputs return 0.'),
        ('BMI categories', 'Below 18.5: underweight; 18.5 to below 25: normal; 25 to below 30: overweight; 30 and above: obese. The UI shows Pending when metrics are absent.'),
        ('BMR', '10*weightKg + 6.25*heightCm - 5*age + sex adjustment. Adjustment is +5 for MALE and -161 for FEMALE.'),
        ('TDEE', 'BMR multiplied by the stored activity-level multiplier.'),
        ('Multipliers', 'Sedentary 1.2; lightly active 1.375; moderately active 1.55; very active 1.725; extra active 1.9; null defaults to 1.2.'),
        ('Calorie target', 'Round TDEE. Underweight: add 250. Overweight/obese: subtract 350, with floor 1400. Normal: use rounded TDEE.'),
        ('Displayed weight range', '18.5*(heightM^2) through 24.9*(heightM^2), formatted to one decimal.'),
    ], [110, WIDTH - 110])
    code('''CALCULATE_PROFILE(user):
  IF height, weight or age needed by a formula is missing: show pending values
  bmi = round(weightKg / square(heightCm / 100), 1 decimal)
  category = classify(bmi)
  bmr = 10*weightKg + 6.25*heightCm - 5*age + sexAdjustment
  tdee = bmr * activityMultiplier
  target = rounded tdee, adjusted by the program's category rule
  persist user; refresh profile, advice and dashboard values''')
    text('These are the rules implemented in UserService, not a personalized clinical assessment. Nutrition and AI outputs are estimates. '
         'Age-specific clinical interpretation, pregnancy adjustments and medical diagnosis are outside this desktop implementation.', 'small')

    heading('7.3 Multidimensional nutrition aggregation', 2)
    text('Let n be the number of this account\'s logged food entries on the requested date. The matrix has n rows and four numeric columns. '
         'Columns have different units, so each is summed separately. Empty input returns zero rows and the dashboard starts from four zero totals.')
    table(['Row example', 'Calories (kcal)', 'Protein (g)', 'Carbs (g)', 'Fat (g)'], [
        ('Food entry 1', 420, 35, 40, 12), ('Food entry 2', 180, 10, 20, 8), ('Column totals', 600, 45, 60, 20),
    ], [110, 100, 100, 100, WIDTH - 410])
    code('''DAILY_NUTRITION_MATRIX(userId, date):
  entries = LOAD and FILTER nutrition records by userId and date
  matrix = new double[entries.count][4]
  FOR row FROM 0 TO entries.count - 1:
    matrix[row] = [calories, proteinG, carbsG, fatG] from entries[row]
  RETURN matrix

HOME_NUTRITION_OVERVIEW(userId, date):
  matrix = DAILY_NUTRITION_MATRIX(userId, date)
  totals = [0, 0, 0, 0]
  FOR EACH row IN matrix:
    FOR column FROM 0 TO 3:
      totals[column] += row[column]
  macroKcal = 4*proteinG + 4*carbsG + 9*fatG
  IF macroKcal > 0:
    percent = 100 * each macro's kcal / macroKcal
  ELSE all macro percentages = 0
  update calorie count, grams, percentages and colored bars''')
    text('The macro bars show each macro\'s share of macro-derived calories, not progress toward a protein/carbohydrate/fat goal. '
         'Total food calories may differ from 4/4/9-derived macro calories. The matrix replaces four repeated nutrition-file reads in the Home summary with one snapshot.')

    heading('7.4 Time ranges and conflict detection', 2)
    text('ActivityTime converts start/end text into minutes after midnight. A start is 00:00-23:59; an end may also be 24:00 (1440 minutes). '
         'The interval is half-open: [start, end). Adjacent activities are valid. A plan ending at 24:00 stays on its stored date and does not overlap the next date\'s 00:00 activity.')
    code('''PARSE_INTERVAL(startText, endText):
  require strict HH:mm start in 00:00..23:59
  start = 60*hour + minute
  end = 1440 IF endText equals "24:00" ELSE parse strict 00:00..23:59
  require start < end
  RETURN [start, end)

CONFLICTS(candidate, existing):
  IF different user, date or scheduling level: false
  IF existing is cancelled OR is the excluded edit ID: false
  RETURN candidate.start < existing.end AND existing.start < candidate.end

SAVE_EVENT(candidate):
  validate interval; for a sub-plan, validate parent bounds
  compare against peer activities using CONFLICTS
  IF any conflict: reject and explain
  ELSE persist and redraw the selected date''')
    table(['Example', 'Expected result'], [
        ('22:00-23:00 beside 23:00-24:00', 'No conflict: touching boundary only.'),
        ('23:00-24:00 beside 23:30-23:59', 'Conflict: overlapping intervals.'),
        ('23:00-00:00 on the same date', 'Rejected: use 24:00 for the midnight end boundary.'),
        ('24:00 as a start, or 24:01 as an end', 'Rejected by strict interval parsing.'),
        ('Sub-plan inside its parent', 'Allowed against the parent; overlapping siblings are checked.'),
    ], [235, WIDTH - 235])

    heading('7.5 Atomic generated plans and cascade deletion', 2)
    code('''ADD_GENERATED_PLAN(proposed):
  all = LOAD activities
  merged = copy(all); usedIds = IDs from all
  FOR EACH candidate IN proposed:
    validate owner, title, ISO date and interval
    reject duplicate ID
    compare against merged same-user, same-date, same-parent peers
    IF conflict: reject ENTIRE plan before any save
    append candidate to merged
  SAVE merged once

DELETE_WITH_DESCENDANTS(activityId):
  all = LOAD activities; removedIds = {activityId}
  REPEAT:
    changed = false
    FOR EACH activity IN all:
      IF activity.parentId IN removedIds:
        changed = add activity.id to removedIds OR changed
  UNTIL no IDs were added
  REMOVE all records whose ID is in removedIds
  SAVE once''')
    text('The deletion set handles arbitrary descendant depth and terminates even if malformed data contains a parent cycle. '
         'Legacy records without parentId remain top-level. Deletion uses the selected button action rather than a special user-entered title such as DELETE.')

    heading('7.6 Searching, sorting, recipe matching and favorites', 2)
    code('''SEARCH_RECIPES(query):
  normalize query to lowercase and trim whitespace
  LOAD catalog; retain recipes whose name, ingredients or tags contain query
  apply requested service filters and comparator when used

MATCH_PANTRY(userId):
  pantryNames = lowercase names in the user's pantry
  FOR EACH catalog recipe WITH at least one ingredient:
    available = count ingredient strings matched by substring in either direction
    missing = ingredient strings with no pantry-name match
    score = 100 * available / ingredientCount
    collect [recipe, score, missing]
  sort matches by descending score

TOGGLE_DISPLAYED_FAVORITE(recipe):
  LOAD/initialize catalog
  FIND catalog record with recipe.id
  IF missing: append the displayed generated recipe
  toggle favorite; SAVE catalog
  IF save fails: restore old state and report the error
  RETURN persisted favorite state''')
    text('Matching is name/substring based; it is not a quantity-aware nutrition planner or allergen certification system. '
         'RecipeService exposes filters/sort methods beyond those surfaced by every current screen. The Add Recipe dialog searches its permitted catalog, '
         'which favors saved favorites and pantry-match records. The primary Meals cards are generated from current in-stock pantry items. '
         'Favorites are catalog-level flags shared by the installation; separate per-account favorites are not implemented.')

    heading('7.7 AI request and chat response lifecycle', 2)
    code('''REQUEST_AI(userContext, prompt):
  prepare a local fallback first
  build JSON request for Gemini generateContent
  dispatch asynchronously with connection and request timeouts
  IF HTTP success AND response parses into usable content: return content
  ELSE return local fallback

SEND_CHAT(message):
  reject empty input or a second send while awaiting a reply
  record user turn; refuse unsupported topics locally
  capture account ID and conversation generation
  request AI asynchronously
  ON JavaFX thread when completed:
    ignore obsolete generation or mismatched active account
    record reply and valid parsed plan tokens
    render in the current chat controller and enable input

CLEAR_CHAT_OR_SWITCH_ACCOUNT:
  clear applicable history; increment generation; clear pending state
  late responses from the older generation cannot repopulate the new conversation''')
    text('The configured model string is read from GeminiService in the source. Access depends on the configured key and provider availability; '
         'a passing fallback-tolerant unit test does not prove the remote account has quota. Chat history lasts for the running process and tab navigation, not across application restarts.')

    heading('7.8 JSON write algorithm and complexity', 2)
    code('''SAVE_ALL(filename, items):
  create data directory if necessary
  create a temporary file in that directory
  serialize items as pretty-printed UTF-8 JSON
  close the writer
  atomically replace destination when supported
  otherwise replace destination using the filesystem fallback
  on I/O failure: throw an error to the caller
  always attempt temporary-file cleanup''')
    table(['Operation', 'Approximate cost (excluding string length and disk latency)'], [
        ('Daily nutrition matrix', 'O(N) filtering of N stored entries plus O(4n) matrix fill/aggregation for n selected entries.'),
        ('Recipe name sorting / search', 'O(R log R) catalog sort, plus a scan of recipe fields and ingredients for searching.'),
        ('Pantry matching', 'O(R*I*P + R log R): R recipes, average I ingredients, P pantry names.'),
        ('Conflict lookup', 'Loads activities; user activities are sorted by date/time, then the selected day is scanned.'),
        ('Generated-plan batch', 'O(B*A + B^2) comparisons for B proposed and A existing activities, followed by one save.'),
        ('Cascade delete', 'O(A*H) fixed-point scans in the worst case for A activities and hierarchy depth H.'),
    ], [140, WIDTH - 140])

    heading('8. Flowcharts and process diagrams', new_page=True)
    text('Notation: rounded green boxes are start/end states; purple boxes are processing steps; amber diamonds are decisions; blue boxes are stored data. '
         'Arrows indicate control or data movement. These are vector diagrams embedded in the PDF, so they remain sharp when printed.')
    heading('8.1 Authentication flowchart', 2)
    diagram([
        ('start', 190, 0, 120, 28, 'start', 'Open Login'),
        ('input', 140, 55, 220, 40, 'process', 'Enter username/email and password'),
        ('valid', 165, 125, 170, 60, 'decision', 'Required fields present?'),
        ('error', 0, 135, 125, 40, 'process', 'Show validation message'),
        ('load', 140, 220, 220, 40, 'store', 'Load users.json; hash password'),
        ('match', 165, 295, 170, 60, 'decision', 'Identity and hash match?'),
        ('denied', 0, 305, 125, 40, 'process', 'Show authentication error'),
        ('session', 140, 390, 220, 40, 'process', 'Set active user; open Main shell'),
        ('end', 190, 460, 120, 28, 'start', 'Dashboard ready'),
    ], [('start', 'input', '', None), ('input', 'valid', '', None),
        ('valid', 'error', 'No', [(165,155),(125,155)]), ('valid','load','Yes',None),
        ('load','match','',None), ('match','denied','No',[(165,325),(125,325)]),
        ('match','session','Yes',None), ('session','end','',None)], 510,
        'Figure 3. Login validation and session creation. Rejected inputs remain outside the authenticated shell.')

    heading('8.2 Activity scheduling flowchart', 2, new_page=True)
    diagram([
        ('start', 180, 0, 140, 28, 'start', 'Add / edit / reschedule'),
        ('input', 140, 55, 220, 40, 'process', 'Collect date, type, title, start/end'),
        ('parse', 140, 120, 220, 40, 'process', 'Parse minutes; preserve end 24:00'),
        ('valid', 165, 190, 170, 62, 'decision', 'Valid range and parent bounds?'),
        ('invalid', 0, 200, 125, 42, 'process', 'Explain invalid range; reject'),
        ('peers', 140, 285, 220, 40, 'store', 'Load same-user/date peer events'),
        ('conflict', 165, 360, 170, 62, 'decision', 'Any interval overlap?'),
        ('blocked', 0, 370, 125, 42, 'process', 'Explain conflict; reject'),
        ('save', 140, 465, 220, 40, 'store', 'Persist checked activity or full batch'),
        ('end', 180, 540, 140, 28, 'start', 'Refresh local calendar'),
    ], [('start','input','',None),('input','parse','',None),('parse','valid','',None),
        ('valid','invalid','No',[(165,221),(125,221)]),('valid','peers','Yes',None),
        ('peers','conflict','',None),('conflict','blocked','Yes',[(165,391),(125,391)]),
        ('conflict','save','No',None),('save','end','',None)], 585,
        'Figure 4. Validated local scheduling. Google Calendar is not a branch of this flow.')

    heading('8.3 Nutrition matrix flowchart', 2, new_page=True)
    diagram([
        ('start',180,0,140,28,'start','Open Home dashboard'),
        ('filter',130,60,240,45,'store','Load and filter entries by user and date'),
        ('matrix',130,140,240,50,'process','Build double[n][4]: kcal, protein, carbs, fat'),
        ('sum',130,225,240,45,'process','Nested loops sum each nutrient column'),
        ('percent',130,305,240,45,'process','Calculate macro calorie shares; guard zero denominator'),
        ('end',130,385,240,35,'start','Display grams, calories and colored progress bars'),
    ], [('start','filter','',None),('filter','matrix','',None),('matrix','sum','',None),
        ('sum','percent','',None),('percent','end','',None)], 445,
        'Figure 5. The multidimensional data requirement is connected to real dashboard output.')
    heading('8.4 AI-assisted recommendation flowchart', 2, new_page=True)
    diagram([
        ('start',180,0,140,28,'start','Request recipes / advice'),
        ('local',135,60,230,45,'process','Read profile, pantry and plans; prepare local fallback'),
        ('http',135,140,230,45,'external','Send asynchronous Gemini request'),
        ('valid',160,220,180,70,'decision','Successful, usable response?'),
        ('fallback',0,320,200,45,'process','Use deterministic local result'),
        ('remote',300,320,200,45,'process','Use parsed remote result'),
        ('render',135,405,230,45,'process','Render on JavaFX thread; ignore stale chat context'),
        ('end',135,490,230,35,'start','Optional favorite / validated plan import'),
    ], [('start','local','',None),('local','http','',None),('http','valid','',None),
        ('valid','fallback','No',[(160,255),(100,255),(100,320)]),
        ('valid','remote','Yes',[(340,255),(400,255),(400,320)]),
        ('fallback','render','',[(100,365),(100,385),(250,385),(250,405)]),
        ('remote','render','',[(400,365),(400,385),(250,385),(250,405)]),
        ('render','end','',None)], 545,
        'Figure 6. AI is optional. Local recommendation and scheduling workflows remain available when the network fails.')

    heading('9. User interface and operating guide', new_page=True)
    heading('9.1 Home and visual language', 2)
    text('Home combines summary cards, today\'s activities, nutrition, advice and upcoming plans. Protein uses violet, carbohydrates amber, fat mint/emerald and calories coral. '
         'Selected navigation uses a theme-specific gradient. BMI category badges use paired foreground/background colors rather than inheriting generic dark-mode label colors.')
    screenshot('home-light.png', 'Figure 7. Home in light mode, using a synthetic demonstration account.')
    screenshot('home-dark.png', 'Figure 8. The same navigation and nutrition hierarchy in dark mode.')
    heading('9.2 Account and profile workflow', 2)
    text('Select Sign up, enter the identity/password fields and body metrics, then create the account. Copy the generated recovery code into a private safe place. '
         'Sign in using username or email. Open My Profile through the profile area in the sidebar to change metrics or preferences. '
         'Save Profile recalculates the target. Account recovery code verifies the current password before replacing a saved code.')
    text('Forgot password asks for username/email, a previously saved recovery code, the new password and confirmation. '
         'An existing account without a recovery code must enroll while it can still sign in. The program does not send recovery email. '
         'The Remember me checkbox is present in the login view, but persistent automatic sign-in is not implemented.')
    screenshot('profile-bmi-dark.png', 'Figure 9. Correctly paired dark-mode BMI category colors. The numerical value and category stay distinct.', 245)
    heading('9.3 Pantry and meals workflow', 2)
    text('Open Pantry and add an ingredient, quantity, unit, category and low-stock threshold. Quantities and thresholds must be finite and non-negative. '
         'Invalid input keeps the ingredient dialog open. Editable category and unit text are retained. Use edit/delete actions to maintain stock.')
    text('Open Meals to see locally generated pantry recipes immediately, with optional Gemini enhancement. A recipe card opens its detail modal. '
         'Favorite saves the displayed recipe into the catalog if it was newly generated. The Add Recipe catalog searches permitted favorites/pantry matches. '
         'Add to meal plan collects a meal category, date and time range and blocks conflicts; Log as Eaten Today creates a NutritionEntry.')
    screenshot('meals-light.png', 'Figure 10. Pantry-driven recipe cards and meal planning in light mode.')
    screenshot('recipe-dark.png', 'Figure 11. Recipe details, macro colors and a persisted favorite in dark mode.')
    heading('9.4 Plan and Activity workflow', 2)
    text('Open Plan for the local Day, Week or Month view. The Day view starts in full-day mode, from 12 AM through 11 PM. '
         'Use 24:00 as an end time, for example 23:00-24:00. Add a sub-plan from its parent block and keep its interval inside the parent. '
         'Use Reschedule, edit, complete, cancel or delete controls as appropriate. Deleting a parent removes its descendants.')
    text('Week event titles wrap. Month cells use compact previews and counts; hover a preview for the full title/time or open the day to inspect all activities. '
         'Narrow windows can scroll the local calendar horizontally rather than squeeze critical controls. Activity shows workout summaries and a filterable list; '
         'its BMI advice grows to its required height and scrolls with the page instead of being cut to one line.')
    screenshot('activity-advice-dark.png', 'Figure 12. A deliberately long synthetic advice message remains fully readable.', 300)
    heading('9.5 Chat and themes', 2)
    text('Chatbot accepts LifeSync-related planning, pantry, nutrition and fitness questions. Unsupported topics receive a local refusal. '
         'Recognized event tokens produce an actionable plan card; Add All validates the entire set before any write. A conflict leaves all items unsaved. '
         'Clear Chat invalidates pending replies. Switching accounts clears the previous account\'s displayed conversation. '
         'The sidebar theme toggle changes the current scene; theme selection is saved for the installation.')

    heading('10. Validation, exceptions and security boundaries', new_page=True)
    table(['Condition', 'Expected response'], [
        ('Empty login fields / bad credentials', 'Visible login message; authentication does not advance.'),
        ('Invalid signup/profile numeric input', 'Field-level/status feedback; NaN and infinite values do not enter the save path.'),
        ('Negative or non-finite pantry stock', 'Dialog remains open; service validation rejects invalid additions/updates before persistence.'),
        ('Invalid start/end or parent bounds', 'A themed time-validation error; no invalid plan write.'),
        ('Overlapping peer activities', 'A conflict message; Activity, Plan and Meals reject the scheduling request.'),
        ('AI plan with a conflict/duplicate/malformed interval', 'The whole batch is rejected before the JSON write; the plan remains available for correction.'),
        ('AI timeout / invalid key / parse failure', 'A local fallback result; the UI remains responsive.'),
        ('Unreadable JSON / failed save', 'Storage error propagation; missing files are different from unreadable files. Corrupt data must not be treated as an empty account store.'),
        ('Invalid / consumed recovery code', 'Reset is rejected without changing the password or consuming another valid code.'),
    ], [180, WIDTH - 180])
    heading('10.1 Credential and data handling', 2)
    text('Passwords use unsalted SHA-256 in the existing educational implementation. This is not a modern password-hardening scheme such as a salted password KDF. '
         'Recovery secrets use SecureRandom and are stored only as hashes; this does not encrypt the remaining JSON data. '
         'A person with filesystem access can read or alter local records. Account separation is enforced by service filters and UI/session logic, not operating-system isolation.')
    text('GEMINI_API_KEY is read from the process environment. run.sh loads the local .env file; Java itself does not automatically load .env. '
         'The supplied .gitignore excludes environment files. The implementation sends selected user context to Gemini when a request is made, so an operator should use appropriate demonstration data for presentations. '
         'No external calendar account, OAuth token or Google Calendar permission is required.')
    heading('10.2 Persistence and session boundaries', 2)
    text('DataStore writes to the directory named by lifesync.data.dir, defaulting to data relative to the working directory. '
         'Tests override this location. Atomic replacement protects one file at a time; fallback filesystems may provide only replacement semantics. '
         'There is no inter-process lock, cross-file transaction, encrypted database, or cloud backup. Run one application instance against one data directory.')

    heading('11. Verification and test evidence', new_page=True)
    text(f'The current Maven reports contain <b>{total} tests, zero failures and zero errors</b>. '
         'Counts below are read from target/surefire-reports rather than estimated. Re-run the suite after any source change and regenerate this document for a new snapshot.')
    table(['JUnit suite', 'Tests', 'Failures', 'Errors', 'Skipped'], [
        (s['suite'], s['tests'], s['failures'], s['errors'], s['skipped']) for s in suites
    ], [WIDTH - 180, 45, 45, 45, 45])
    heading('11.1 Requirement-audit regression cases', 2)
    table(['Case', 'Expected / verified behavior'], [
        ('Matrix shape and column mapping', 'Multiple daily entries retain kcal/protein/carbs/fat in the documented columns.'),
        ('Matrix account/date isolation', 'Another account or date does not contribute to the requested snapshot.'),
        ('Matrix consistency', 'Column sums agree with the existing four total getters.'),
        ('Matrix ownership', 'Changing the returned array cannot modify saved nutrition records.'),
        ('Generated favorite reload', 'A new generated recipe is present and favorited after a catalog reload.'),
        ('Generated unfavorite / duplicate prevention', 'Unfavoriting persists without inserting a duplicate ID.'),
        ('Existing catalog favorite', 'Toggling a catalog recipe retains the rest of the catalog.'),
    ], [175, WIDTH - 175])
    heading('11.2 Desktop verification and limits of the evidence', 2)
    text('Desktop checks exercised the existing navigation in both themes, macro totals and gradients, the recipe detail modal, live recipe-backdrop theme changes '
         'and generated favorite persistence. Earlier targeted regression checks covered all four BMI classes plus Pending, long advice text, two window sizes, '
         'pantry form recovery, the DELETE-title regression, late-night scheduling and chat account isolation. A 42-case screen sweep checked labels/buttons '
         'and Calendar Day/Week/Month layouts. Compact month previews intentionally use ellipsis with full-text tooltips.')
    text('The Maven FXML test proves well-formed XML, not every rendered layout. Gemini tests accept useful fallback output, so their success does not certify remote quotas, '
         'medical correctness or cloud availability. Desktop checks were performed on this macOS workspace; Windows packaging and every display/font combination were not independently certified.')
    heading('11.3 Suggested acceptance demonstration', 2)
    table(['Step', 'Pass condition'], [
        ('Register a synthetic account; save recovery code', 'Account can sign in and the code is displayed once.'),
        ('Set 170 cm and 48 kg; switch to dark mode', 'BMI category is readable and advice remains fully accessible.'),
        ('Add pantry stock and inspect Meals', 'Local recipes appear; favorite one, reload catalog, and find it saved.'),
        ('Log two foods', 'Home totals equal the matrix column sums; macro shares sum approximately to 100% after rounding.'),
        ('Schedule 23:00-24:00, then attempt 23:30-23:59', 'First saves; overlapping peer is rejected; next day 00:00 is independent.'),
        ('Create parent, child and grandchild; delete parent', 'All descendants disappear while unrelated events remain.'),
        ('Import an AI plan containing one conflict', 'No proposed items are partially inserted.'),
        ('Switch accounts after a chat exchange', 'The prior account\'s message is not shown in the new account.'),
        ('Restart application', 'Persistent records/theme remain; active login and in-memory chat are recreated as documented.'),
    ], [230, WIDTH - 230])

    heading('12. Installation, execution and deployment', new_page=True)
    heading('12.1 Runtime prerequisites', 2)
    table(['Component', 'Repository configuration'], [
        ('Java compiler/runtime', 'Java 21; maven-compiler-plugin uses release 21.'),
        ('JavaFX', 'javafx-controls and javafx-fxml 21.0.6.'),
        ('Persistence', 'Gson 2.11.0; writable local data directory.'),
        ('Icons', 'Ikonli JavaFX / FontAwesome5 pack 12.3.1.'),
        ('Tests', 'JUnit Jupiter 5.12.1; Maven Surefire 3.2.5.'),
        ('Build wrapper', 'Maven Wrapper with the distribution configured in .mvn/wrapper/maven-wrapper.properties.'),
        ('Network', 'Needed for initial Maven dependency downloads and optional Gemini requests, not local CRUD after setup.'),
    ], [140, WIDTH - 140])
    heading('12.2 macOS / Linux-style shell commands', 2)
    code('''# Run from the project directory with a Java 21 JDK selected:
sh mvnw test
sh mvnw javafx:run

# This repository's macOS launcher selects the installed Java 21 and loads .env:
./run.sh

# Generic explicit selection when Java 21 is not the default:
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
sh mvnw test''')
    heading('12.3 Windows PowerShell commands', 2)
    code(r'''cd path\to\LifeSync
$env:JAVA_HOME = "C:\path\to\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
.\mvnw.cmd javafx:run

# Optional AI key for the current process:
$env:GEMINI_API_KEY = "YOUR_KEY_HERE"
.\mvnw.cmd javafx:run''')
    text('Use placeholders above only as instructions; never put a real API key in submitted documentation. On Windows or in an IDE, set GEMINI_API_KEY in the run configuration/environment. '
         'The macOS run.sh helper is not a Windows .env loader. If JAVA_HOME is unset or Java 8 is active, select a Java 21 JDK before building.')
    heading('12.4 Build and release handoff', 2)
    code('''sh mvnw clean test
sh mvnw -DskipTests package
# Primary runnable development entry point:
sh mvnw javafx:run''')
    text('The ordinary Maven JAR is not claimed to be a standalone executable bundle with every JavaFX native library. '
         'Use the configured JavaFX launch path for demonstration. A platform-specific installer or runtime image needs its own packaging and target-OS test. '
         'Back up data before replacing an application build and preserve the working-directory convention so the same local data folder is used.')

    heading('13. Operations, maintenance and troubleshooting', new_page=True)
    heading('13.1 Backup, restore and data ownership', 2)
    text('Close the application before taking or restoring a consistent backup. Copy the full data directory to a dated backup location. '
         'To restore, preserve the current directory as a separate backup, replace data with the known-good copy, then reopen and verify a synthetic/read-only workflow first. '
         'Do not edit live JSON while the application is saving, and do not run multiple instances against the same records.')
    heading('13.2 Troubleshooting matrix', 2)
    table(['Symptom', 'Check / corrective action'], [
        ('Unsupported release / compile failure', 'Confirm java -version and JAVA_HOME select Java 21, then re-run the Maven wrapper.'),
        ('AI output is local or unavailable', 'Check that the actual run process received GEMINI_API_KEY, network access and provider availability. Local fallback remains expected behavior.'),
        ('23:00-00:00 is rejected', 'Use 23:00-24:00 for the end of the same stored date.'),
        ('Plan cannot be added', 'Review same-day peers, sibling sub-plans and duplicates. A generated batch is rejected as a whole.'),
        ('Forgot-password reset fails', 'Use the saved, unused recovery code for that account. An email address alone cannot reset it.'),
        ('Records appear missing', 'Check the process working directory and lifesync.data.dir. Test data and production data are intentionally separate.'),
        ('JSON read/write error', 'Check permissions, free space and file validity; preserve the faulty file and restore a known-good backup rather than replacing it with an empty list.'),
        ('Category or advice appears cut off', 'Use the updated styles/FXML, restart the application and inspect both themes/window sizes before changing layout constraints.'),
    ], [185, WIDTH - 185])
    heading('13.3 Documentation regeneration', 2)
    code('''# Documentation tooling is optional; it is not an application runtime dependency.
python3 -m venv .venv-docs
# Activate the virtual environment for your operating system, then:
python -m pip install -r docs/requirements.txt
python tools/generate_program_documentation.py''')
    text('The generator reads source, test XML and synthetic screenshot assets. It writes LifeSync_Program_Documentation.pdf, '
         'docs/requirements_traceability.json and docs/verification_summary.json. It does not read .env or actual account JSON. '
         'Re-run tests first; refresh screenshots after visible UI changes. Render and inspect the PDF before handing it off.')
    heading('13.4 Maintenance checklist', 2)
    text('For a future change: identify the affected requirement; preserve current user records; implement at the owning service/controller; add a regression case when behavior changes; '
         'run the full suite; inspect both themes; check late-night and nested schedules when time logic changes; update diagrams/algorithms and regenerate this manual. '
         'Avoid declaring a feature complete solely because a placeholder control exists.')

    heading('14. Limitations, glossary and source index', new_page=True)
    heading('14.1 Explicit limitations', 2)
    text('Google Calendar is excluded. There is no external calendar synchronization, OAuth flow or calendar notification service. '
         'The application is a local desktop prototype with JSON persistence, not a concurrent server or clinical system. '
         'Recipes/favorite flags and theme selection are installation-wide; other primary records are scoped by userId. '
         'Chat and login session state do not survive a restart. Persistent Remember me behavior is not implemented. '
         'Recipe matching is lexical and does not prove stock quantities, ingredient safety or nutrient accuracy. '
         'AI output is provider-dependent and may be replaced by local fallback. Per-account favorites, encrypted storage, stronger password KDFs and platform installers would require separate scoped work.')
    heading('14.2 Glossary', 2)
    table(['Term', 'Meaning in this program'], [
        ('Activity', 'A dated local scheduled interval: workout, meal, study, rest or other.'),
        ('BMI / BMR / TDEE', 'Body Mass Index / estimated Basal Metabolic Rate / estimated Total Daily Energy Expenditure as computed by UserService.'),
        ('CRUD', 'Create, read, update and delete record operations.'),
        ('FXML', 'Declarative JavaFX view markup loaded by FXMLLoader.'),
        ('Gson / JSON', 'The serializer and structured text representation used for persistence.'),
        ('Half-open interval', 'An interval including its start but excluding its end; adjacent slots do not overlap.'),
        ('Matrix', 'A two-dimensional double array; daily food entries are rows and four nutrient metrics are columns.'),
        ('Recovery code', 'A saved high-entropy one-time secret used to authorize a local password reset.'),
        ('Scope guard / generation', 'Topic checking and an in-memory version used to ignore obsolete chat responses.'),
        ('SDLC', 'The planning-through-maintenance development process summarized in the supplied screenshot.'),
    ], [135, WIDTH - 135])
    heading('14.3 Implementation reference index', 2)
    refs = [
        ('Startup/navigation', 'Launcher.java; LifeSyncApplication.java; SceneManager.java'),
        ('Authentication/recovery', 'service/AuthService.java; controller/LoginController.java; controller/AccountRecoveryDialogs.java'),
        ('Profile calculations', 'service/UserService.java; model/User.java; controller/ProfileController.java'),
        ('Nutrition matrix', 'service/NutritionService.java; model/NutritionEntry.java; controller/HomeController.java'),
        ('Scheduling/hierarchy', 'service/ActivityService.java; model/Activity.java; model/ActivityTime.java; controller/PlanController.java'),
        ('Pantry and recipes', 'service/PantryService.java; service/RecipeService.java; controller/PantryController.java; controller/MealsController.java'),
        ('AI and conversation', 'service/GeminiService.java; service/ChatScopeValidator.java; controller/ChatController.java'),
        ('Persistence/theme', 'service/DataStore.java; service/ThemeManager.java; resources/com/lifesync/lifesync/css/'),
        ('Verification', 'src/test/java/; target/surefire-reports/; docs/verification_summary.json'),
        ('Proposal evidence', 'Unknown.jpeg; Unknown-2.jpeg; docs/requirements_traceability.json'),
    ]
    table(['Area', 'Primary repository source'], refs, [130, WIDTH - 130])
    heading('14.4 Handoff summary', 2)
    text(f'The screenshot programming requirements are mapped to code, the explicit nutrition matrix and generated-favorite persistence gaps are implemented, '
         f'and the current Maven suite reports {total} passing tests with zero failures/errors. '
         'This PDF, its generator, requirement traceability and verification summary form the documentation handoff. '
         'Google Calendar remains outside the implementation scope.')

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    document = Manual(OUTPUT)
    document.multiBuild(STORY)
    print(json.dumps({'pdf': str(OUTPUT), 'bytes': OUTPUT.stat().st_size,
                      'tests': total, 'source_snapshot_sha256': snapshot}, indent=2))


if __name__ == '__main__':
    build()
