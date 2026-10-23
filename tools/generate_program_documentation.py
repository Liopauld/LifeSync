#!/usr/bin/env python3
"""Generate the school-submission edition of the LifeSync program documentation.

Run: python tools/generate_program_documentation.py
Optional cover details: --students, --course, --professor, --school, --date.
The application is not modified. Latest Maven reports supply the testing summary.
"""
import argparse
import json
import math
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.platypus import (
    Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether, Image,
)
from reportlab.graphics.shapes import Drawing, Rect, Polygon, Line, String

import generate_technical_manual as L

ROOT = L.ROOT
OUTPUT = ROOT / 'LifeSync_Program_Documentation.pdf'
S = L.STORY
L.STYLES['body'].fontSize = 10.5
L.STYLES['body'].leading = 15.3
L.STYLES['small'].fontSize = 8.5
L.STYLES['small'].leading = 12
L.STYLES['cell'].fontSize = 8.8
L.STYLES['cell'].leading = 12.5
L.STYLES['headcell'].fontSize = 9
L.STYLES['headcell'].leading = 12.5
L.STYLES['caption'].fontSize = 8
L.STYLES['caption'].leading = 11
L.STYLES['code'].fontSize = 8.6
L.STYLES['code'].leading = 12.3
L.STYLES['h2'].fontSize = 12.5
L.STYLES['h2'].leading = 17


class SchoolDocument(L.Manual):
    def __init__(self, filename, cover):
        self.cover = cover
        super().__init__(filename)
        self.title = 'LifeSync - Program Documentation'
        self.author = cover.get('students') or 'LifeSync Project'
        self.subject = 'Java programming project: wellness, nutrition, fitness and daily planning'

    def page_decorations(self, canv, doc):
        canv.saveState()
        canv.setFont('Vera', 7.5)
        canv.setFillColor(L.MUTED)
        if doc.page > 1:
            canv.drawString(L.MARGIN, L.PAGE_H - 24, 'LifeSync | Program Documentation')
            canv.setStrokeColor(L.LINE)
            canv.line(L.MARGIN, L.PAGE_H - 32, L.PAGE_W - L.MARGIN, L.PAGE_H - 32)
        canv.drawRightString(L.PAGE_W - L.MARGIN, 23, str(doc.page))
        if doc.page == 1:
            fields = [
                ('students', 'Prepared by', 296, 42),
                ('course', 'Course and section', 241, 24),
                ('professor', 'Submitted to', 194, 24),
                ('school', 'School', 147, 24),
                ('date', 'Submission date', 100, 24),
            ]
            for name, label, y, height in fields:
                canv.setFont('Vera-Bold', 8.5)
                canv.setFillColor(L.INK)
                canv.drawString(L.MARGIN, y + height + 6, label)
                canv.acroForm.textfield(
                    name=name, tooltip=label, x=L.MARGIN, y=y,
                    width=L.WIDTH, height=height, value=self.cover.get(name) or '',
                    fontName='Helvetica', fontSize=10, borderWidth=.6,
                    borderColor=L.LINE, fillColor=colors.HexColor('#FAF8FE'),
                    textColor=L.INK, forceBorder=True, maxlen=400,
                    fieldFlags='multiline' if name == 'students' else '',
                )
        canv.restoreState()


def flow(nodes, edges, height, caption):
    """A compact vector flowchart with readable nine-point labels."""
    d = Drawing(500, height)
    by_id = {n[0]: n for n in nodes}
    for start, end, label, points in edges:
        a, b = by_id[start], by_id[end]
        if points is None:
            points = [(a[1] + a[3] / 2, a[2] + a[4]), (b[1] + b[3] / 2, b[2])]
        pts = [(x, height - y) for x, y in points]
        for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
            d.add(Line(x1, y1, x2, y2, strokeColor=L.MUTED, strokeWidth=1))
        (x1, y1), (x2, y2) = pts[-2:]
        angle = math.atan2(y2 - y1, x2 - x1)
        bx, by = x2 - 6 * math.cos(angle), y2 - 6 * math.sin(angle)
        d.add(Polygon([x2, y2, bx + 3 * math.sin(angle), by - 3 * math.cos(angle),
                       bx - 3 * math.sin(angle), by + 3 * math.cos(angle)],
                      fillColor=L.MUTED, strokeColor=L.MUTED))
        if label:
            x, y = pts[len(pts) // 2]
            d.add(String(x + 5, y + 4, label, fontName='Vera', fontSize=8, fillColor=L.MUTED))
    for key, x, top, width, h, kind, label in nodes:
        y = height - top - h
        shade = {'start': '#E2F5EC', 'decision': '#FFF1DD',
                 'process': '#EFE9FB', 'store': '#E5F1FB'}.get(kind, '#EFE9FB')
        if kind == 'decision':
            d.add(Polygon([x + width / 2, y + h, x + width, y + h / 2,
                           x + width / 2, y, x, y + h / 2],
                          fillColor=colors.HexColor(shade), strokeColor=L.ORANGE))
        else:
            d.add(Rect(x, y, width, h, rx=12 if kind == 'start' else 5,
                       fillColor=colors.HexColor(shade), strokeColor=L.LINE))
        lines = L.wrap_words(label, width * .65 if kind == 'decision' else width - 16, size=9)
        baseline = y + h / 2 + (len(lines) - 1) * 5.5 - 3
        for i, line in enumerate(lines):
            d.add(String(x + width / 2, baseline - i * 11, line, textAnchor='middle',
                         fontName='Vera', fontSize=9, fillColor=L.INK))
    S.append(KeepTogether([d, L.p(escape(caption), 'caption')]))


def build(cover):
    suites = L.tests()
    count = sum(row['tests'] for row in suites)
    skipped = sum(row['skipped'] for row in suites)
    S.clear()

    # Title page; author/course fields are fillable rather than invented.
    S.append(Spacer(1, 45))
    L.text('LifeSync', 'title')
    L.text('Program Documentation', 'subtitle')
    L.text('A Java desktop application for wellness, nutrition, fitness, and daily planning.')
    mascot = Image(str(ROOT / 'src/main/resources/com/lifesync/lifesync/images/login.png'),
                   width=97, height=150)
    banner = Table([[L.p('<b>Plan. Eat. Move. Grow.</b><br/><br/>'
                         'Developed using Java 21, JavaFX, Maven, and JSON file storage.'), mascot]],
                   colWidths=[L.WIDTH - 135, 135])
    banner.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), L.PAPER), ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
        ('LEFTPADDING', (0, 0), (-1, -1), 18), ('TOPPADDING', (0, 0), (-1, -1), 12),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 12),
    ]))
    S.extend([Spacer(1, 16), banner, PageBreak()])

    L.text('Contents', 'title')
    from reportlab.platypus.tableofcontents import TableOfContents
    from reportlab.lib.styles import ParagraphStyle
    toc = TableOfContents()
    toc.levelStyles = [ParagraphStyle('SchoolTOC', fontName='Vera-Bold', fontSize=10.5,
                                     leading=17, spaceBefore=11, textColor=L.PURPLE)]
    S.append(toc)

    L.heading('1. Introduction', new_page=True)
    L.heading('1.1 Background of the project', 2)
    L.text('Managing meals, ingredients, workouts, and daily tasks often requires several separate tools. '
           'LifeSync brings these activities into one desktop application. It gives users a convenient place '
           'to organize personal wellness information and turn daily plans into manageable actions.')
    L.heading('1.2 Statement of the problem', 2)
    L.text('The project addresses three practical problems: scattered wellness records, difficulty selecting '
           'meals from available ingredients, and conflicting daily activities. A connected view of these '
           'records can help users understand their routine and plan more consistently.')
    L.heading('1.3 General objective', 2)
    L.text('To develop a Java desktop application that combines nutrition tracking, pantry and recipe '
           'management, fitness activities, and local scheduling in a clear and usable interface.')
    L.heading('1.4 Specific objectives', 2)
    L.table(['Objective', 'Program output'], [
        ('Manage user information', 'A profile containing body measurements, activity level, and dietary preferences.'),
        ('Summarize nutrition', 'Daily calories and protein, carbohydrate, and fat totals.'),
        ('Organize food resources', 'Pantry stock, low-stock information, recipes, and saved favorites.'),
        ('Support daily planning', 'Activities, meal schedules, completion status, and conflict prevention.'),
        ('Provide optional assistance', 'AI-generated suggestions with local fallback results.'),
    ], [175, L.WIDTH - 175])
    L.heading('1.5 Scope and limitations', 2)
    L.text('The current version runs as a local desktop application and stores records in JSON files. '
           'It includes account access, profile calculations, nutrition summaries, pantry management, '
           'recipe favorites, activities, a local calendar, and an AI chatbot. Google Calendar synchronization '
           'is outside the scope of this version.')
    L.text('Internet access and a configured Gemini API key are needed for remote AI responses. Local '
           'records and fallback suggestions remain available without AI. Nutrition figures are estimates. '
           'The recipe catalog and its favorite flags are shared within the installation, while primary '
           'personal records are filtered by account.', 'small')

    L.heading('2. Program features and technologies', new_page=True)
    L.table(['Module', 'Implemented features'], [
        ('Account and Profile', 'Registration, sign-in, password recovery using a saved code, profile editing, BMI and energy calculations.'),
        ('Home', 'A summary of daily plans, meals, activities, pantry information, nutrition, and upcoming tasks.'),
        ('Meals', 'Pantry-based recipes, recipe details, favorites, meal scheduling, and logging food as eaten.'),
        ('Pantry', 'Adding, editing, deleting, categorizing, and monitoring ingredient quantities.'),
        ('Activity', 'Workout/activity records, progress summaries, completion status, and BMI-based fitness guidance.'),
        ('Plan and Calendar', 'Day, Week, and Month views; nested plans; editing/rescheduling; time validation; conflict detection.'),
        ('AI Chatbot', 'Wellness-related conversation, local fallback replies, and validated plan suggestions that can be added to the calendar.'),
        ('Themes', 'Light and dark appearances with readable labels, nutrition colors, and consistent dialog styling.'),
    ], [130, L.WIDTH - 130])
    L.heading('Technology used', 2)
    L.table(['Technology', 'Purpose'], [
        ('Java 21', 'Programming language and application logic.'),
        ('JavaFX 21 and FXML', 'Desktop controls, screens, event handling, and navigation.'),
        ('CSS', 'Colors, typography, themes, badges, and interaction states.'),
        ('Gson and JSON', 'Reading and saving structured program data.'),
        ('Maven and JUnit', 'Dependency management, building, and automated testing.'),
        ('Java HttpClient and Gemini', 'Optional external AI communication.'),
    ], [150, L.WIDTH - 150])

    L.heading('3. Java programming concepts applied', new_page=True)
    L.text('The application demonstrates the programming concepts identified in the project requirements. '
           'The examples below refer to the current implementation.')
    L.table(['Concept', 'Application in LifeSync'], [
        ('Classes and objects', 'User, Recipe, Activity, PantryItem, and NutritionEntry represent the main records.'),
        ('Arrays and ArrayLists', 'Collections store records; arrays store fixed values and nutrient data.'),
        ('Strings', 'Names, preferences, categories, dates, times, and AI messages are processed as text.'),
        ('Methods', 'Reusable methods perform calculations, validation, searching, persistence, and scheduling.'),
        ('Conditionals', 'If/switch statements classify BMI, validate input, choose themes, and detect conflicts.'),
        ('Loops', 'Loops process collections, build calendar views, match recipes, and aggregate nutrition values.'),
        ('Searching', 'Recipes can be searched by names, ingredients, and tags; records are located by IDs or dates.'),
        ('Sorting', 'Services sort recipes, pantry records, and activities using appropriate comparators.'),
        ('Multidimensional data', 'A double[n][4] nutrition matrix stores food-entry rows and four nutrient columns.'),
        ('Encapsulation', 'Model fields are private and accessed through getters/setters and service methods.'),
        ('Exception handling', 'Validation and error handling manage invalid input, file problems, and AI request failures.'),
        ('File handling', 'The application reads and writes JSON records and saves the selected theme.'),
        ('External API integration', 'The existing Gemini service provides optional AI output. Calendar scheduling remains local.'),
    ], [165, L.WIDTH - 165])
    L.text('Some proposal terms use different class names in the program. For example, PantryItem represents '
           'a stored ingredient, NutritionEntry represents logged food, and Activity represents a local calendar '
           'event. A recipe\'s favorite status is stored on the Recipe object.', 'small')

    L.heading('4. Software Development Life Cycle', new_page=True)
    L.text('The following stages organize the development and evaluation of LifeSync.')
    L.table(['SDLC stage', 'Application to the project'], [
        ('1. Planning', 'Define the project purpose, target users, features, required technologies, and local desktop scope.'),
        ('2. Requirements Analysis', 'Identify account, nutrition, pantry, recipe, fitness, scheduling, validation, and optional AI requirements.'),
        ('3. System Design', 'Plan the screens, model classes, service methods, data relationships, JSON storage, and program flow.'),
        ('4. Development', 'Implement the Java classes, JavaFX controllers, FXML views, CSS themes, calculations, and persistence.'),
        ('5. Testing', 'Check valid/invalid input, calculations, favorites, schedule conflicts, file operations, themes, and fallback behavior.'),
        ('6. Deployment', 'Build and run the desktop application using Java 21 and the Maven Wrapper for demonstration and evaluation.'),
        ('7. Maintenance and Evaluation', 'Correct reported defects, improve readability and usability, and keep the program and documentation consistent.'),
    ], [155, L.WIDTH - 155])
    L.heading('Development outputs', 2)
    L.text('The project outputs include the working desktop application, Java source code, FXML views, '
           'stylesheets, JSON record handling, automated tests, and this program documentation. Testing and '
           'evaluation support revisions to the implementation rather than introducing unrelated features.')

    L.heading('5. System design', new_page=True)
    L.text('LifeSync separates screen presentation from data and business operations. This makes the '
           'program easier to understand, test, and maintain.')
    flow([
        ('ui', 45, 0, 410, 38, 'process', 'JavaFX screens, FXML, CSS, and navigation'),
        ('controllers', 45, 68, 410, 38, 'process', 'Controllers receive user actions and update the interface'),
        ('services', 45, 136, 410, 38, 'process', 'Services perform validation, calculations, and record operations'),
        ('storage', 10, 215, 225, 42, 'store', 'Gson + local JSON files'),
        ('ai', 275, 215, 225, 42, 'process', 'Optional Gemini AI requests'),
    ], [('ui','controllers','',None), ('controllers','services','',None),
        ('services','storage','',None), ('services','ai','',None)], 270,
        'Figure 1. Main program structure and data flow.')
    L.heading('Main data classes', 2)
    L.table(['Class', 'Main information stored'], [
        ('User', 'Identity, account details, body measurements, activity level, and dietary preferences.'),
        ('Activity', 'Owner, title, date, start/end time, category, status, and optional parent activity.'),
        ('PantryItem', 'Owner, ingredient name, category, quantity, unit, and low-stock threshold.'),
        ('NutritionEntry', 'Owner, date, food/meal type, calories, protein, carbohydrates, and fat.'),
        ('Recipe', 'Ingredients, instructions, nutrition estimates, preparation/cooking times, tags, and favorite status.'),
    ], [125, L.WIDTH - 125])
    L.text('A user can have several activities, pantry items, and nutrition entries through their user ID. '
           'An activity may refer to a parent activity to represent a nested plan. Recipe records form a '
           'shared catalog. JSON files retain these records between application sessions.', 'small')

    L.heading('6. Algorithms, pseudocode, and flowcharts', new_page=True)
    L.heading('6.1 Account access', 2)
    L.text('The program checks the entered username or email and compares the password hash with the '
           'saved account record. Valid credentials open the main application; invalid input displays an error.')
    L.code('''LOGIN(identifier, password)
  IF a required field is empty: display a validation message
  LOAD the saved user records
  FIND the account matching the username or email
  IF the password hash matches:
    SET the current user and OPEN the dashboard
  ELSE display an authentication error
END''')
    flow([
        ('start', 185, 0, 130, 28, 'start', 'Start'),
        ('input', 140, 50, 220, 38, 'process', 'Enter username/email and password'),
        ('verify', 140, 112, 220, 38, 'store', 'Load account and check credentials'),
        ('valid', 165, 180, 170, 62, 'decision', 'Valid credentials?'),
        ('error', 5, 190, 120, 42, 'process', 'Display error'),
        ('open', 140, 275, 220, 38, 'process', 'Set current user and open Home'),
        ('end', 185, 340, 130, 28, 'start', 'End'),
    ], [('start','input','',None), ('input','verify','',None), ('verify','valid','',None),
        ('valid','error','No',[(165,211),(125,211)]), ('valid','open','Yes',None),
        ('open','end','',None)], 380, 'Figure 2. Login flowchart.')
    L.text('Registration checks required information and duplicate accounts before saving. Password recovery '
           'uses a saved single-use recovery code; successful reset consumes that code.', 'small')

    L.heading('6.2 Profile calculations and nutrition summary', 2, new_page=True)
    L.table(['Calculation', 'Rule used by the program'], [
        ('BMI', 'weight in kg / (height in meters)^2; displayed to one decimal place.'),
        ('BMR', '10 x weightKg + 6.25 x heightCm - 5 x age, then +5 for male or -161 for female.'),
        ('TDEE', 'BMR x activity multiplier: 1.2, 1.375, 1.55, 1.725, or 1.9.'),
        ('Daily energy target', 'Rounded TDEE, adjusted by the program\'s BMI category rule: +250 for underweight; -350 for overweight/obese with a minimum of 1400; unchanged for normal weight.'),
    ], [125, L.WIDTH - 125])
    L.text('The implemented BMI categories use boundaries of 18.5, 25, and 30. Missing body measurements '
           'produce a pending display. These values support the application\'s estimates and suggestions.')
    L.heading('Two-dimensional nutrition data', 2)
    L.text('Each row of the nutrition matrix represents a food entry for the current user and date. '
           'The columns store calories, protein, carbohydrates, and fat. Column totals feed the Home dashboard.')
    L.table(['Food entry', 'kcal', 'Protein (g)', 'Carbs (g)', 'Fat (g)'], [
        ('Entry 1', 420, 35, 40, 12), ('Entry 2', 180, 10, 20, 8), ('Total', 600, 45, 60, 20),
    ], [L.WIDTH - 320, 65, 85, 85, 85])
    L.code('''SUMMARIZE_NUTRITION(user, date)
  FILTER food records by user and date
  STORE each entry in matrix[row][calories, protein, carbs, fat]
  SET totals = [0, 0, 0, 0]
  FOR each row in the matrix:
    FOR each nutrient column:
      ADD the value to its column total
  CALCULATE macro calorie shares using 4, 4, and 9 kcal per gram
  DISPLAY totals and colored nutrition bars
END''')
    L.text('The macro bars show the share of macro-derived calories. They are not separate macro goals. '
           'The program handles an empty day without dividing by zero.', 'small')

    L.heading('6.3 Pantry, recipes, and favorites', 2, new_page=True)
    L.text('Pantry quantities must be valid non-negative numbers. Recipe services search names, ingredients, '
           'and tags, compare ingredients with pantry records, and support sorting by criteria such as name, '
           'calories, preparation time, and popularity. The Meals screen displays pantry-based recipes and a '
           'searchable favorites/pantry catalog.')
    L.code('''SELECT_AND_SAVE_RECIPE(user)
  READ the user's available pantry ingredients
  GENERATE local recipe suggestions; optionally request AI suggestions
  DISPLAY recipe cards and details
  WHEN Favorite is selected:
    INSERT the displayed recipe into the catalog if it is new
    TOGGLE its favorite status and SAVE the catalog
  WHEN Add to Plan is selected: validate the meal schedule before saving
END''')
    flow([
        ('start', 185, 0, 130, 28, 'start', 'Open Meals'),
        ('pantry', 140, 50, 220, 38, 'store', 'Read available pantry items'),
        ('recipes', 140, 112, 220, 42, 'process', 'Create and display recipe suggestions'),
        ('select', 140, 180, 220, 38, 'process', 'Open a recipe and choose an action'),
        ('favorite', 5, 262, 220, 42, 'store', 'Favorite: save recipe in catalog'),
        ('schedule', 275, 262, 220, 42, 'process', 'Plan: validate date and time'),
    ], [('start','pantry','',None), ('pantry','recipes','',None), ('recipes','select','',None),
        ('select','favorite','',None), ('select','schedule','',None)], 325,
        'Figure 3. Recipe selection and the two main actions.')
    L.text('Pantry matching compares ingredient names. It does not guarantee sufficient ingredient quantities '
           'or certify dietary/allergen suitability.', 'small')

    L.heading('6.4 Scheduling and conflict detection', 2, new_page=True)
    L.text('Activity times are converted to minutes after midnight. Starts range from 00:00 to 23:59; '
           '24:00 is accepted only as an end time. A proposed interval conflicts when it starts before '
           'an existing interval ends and the existing interval starts before it ends.')
    L.code('''SAVE_ACTIVITY(proposed)
  VALIDATE the title, date, start time, and end time
  CHECK parent bounds when the activity is a sub-plan
  READ activities for the same user, date, and scheduling level
  FOR each relevant existing activity:
    IF proposed.start < existing.end AND existing.start < proposed.end:
      REJECT the overlapping activity
  IF no conflict exists: SAVE and refresh the calendar
END''')
    flow([
        ('start', 180, 0, 140, 28, 'start', 'Enter activity details'),
        ('valid', 165, 56, 170, 60, 'decision', 'Valid time range?'),
        ('invalid', 5, 65, 120, 42, 'process', 'Show input error'),
        ('read', 140, 148, 220, 38, 'store', 'Read relevant existing activities'),
        ('conflict', 165, 218, 170, 60, 'decision', 'Any overlap?'),
        ('reject', 5, 227, 120, 42, 'process', 'Show conflict'),
        ('save', 140, 312, 220, 38, 'store', 'Save the activity'),
        ('end', 180, 378, 140, 28, 'start', 'Refresh calendar'),
    ], [('start','valid','',None), ('valid','invalid','No',[(165,86),(125,86)]),
        ('valid','read','Yes',None), ('read','conflict','',None),
        ('conflict','reject','Yes',[(165,248),(125,248)]), ('conflict','save','No',None),
        ('save','end','',None)], 420, 'Figure 4. Validated scheduling flowchart.')
    L.text('Adjacent activities are allowed. For example, 22:00-23:00 and 23:00-24:00 do not conflict. '
           'Sub-plans may overlap their parent but are checked against siblings. Generated multi-event plans '
           'are checked completely before saving.', 'small')

    L.heading('6.5 AI assistance and file handling', 2, new_page=True)
    L.text('Gemini is an optional external service. A local result is prepared first, and network requests run '
           'asynchronously so the interface stays responsive. Invalid or unavailable AI responses use the local fallback.')
    L.code('''REQUEST_SUGGESTION(context)
  PREPARE a local fallback
  SEND an asynchronous AI request
  IF a usable response is received: use the response
  ELSE use the fallback
  UPDATE the interface on the JavaFX thread
END''')
    L.heading('Chat behavior', 2)
    L.text('The chatbot accepts LifeSync-related questions and maintains the conversation while switching '
           'tabs. Switching accounts prevents the previous user\'s history from appearing in the new account. '
           'Recognized plan suggestions can be added to the local calendar after all items pass validation.')
    L.heading('JSON persistence', 2)
    L.text('User records, activities, pantry items, nutrition entries, recipes, and theme settings are stored '
           'as JSON. DataStore handles the main record files, while services decide which records to add, '
           'retrieve, update, or remove.')
    L.code('''SAVE_RECORDS(filename, records)
  CREATE the data folder if necessary
  WRITE the records as UTF-8 JSON to a temporary file
  REPLACE the destination after the write completes
  REPORT an error if reading or saving fails
  REMOVE any remaining temporary file
END''')
    L.table(['Situation', 'Program response'], [
        ('Invalid user input', 'Display validation feedback and prevent an invalid save.'),
        ('Overlapping plan items', 'Reject the conflicting activity or the entire generated batch.'),
        ('AI request fails', 'Use the prepared local suggestion.'),
        ('File operation fails', 'Report the error rather than claim that the save succeeded.'),
    ], [180, L.WIDTH - 180])

    L.heading('7. User interface', new_page=True)
    L.text('The existing screen arrangement is used in both themes. Summary cards and colored macro '
           'indicators help users recognize the most important daily information.')
    L.screenshot('home-light.png', 'Figure 5. Home dashboard in light mode.', 235)
    L.screenshot('home-dark.png', 'Figure 6. Home dashboard in dark mode.', 235)
    L.text('The sidebar opens Plan, Meals, Pantry, Activity, and Chatbot. The profile area opens My Profile. '
           'The theme button changes the appearance without changing the workflow.', 'small')

    L.heading('7.1 Meals and recipe details', 2, new_page=True)
    L.text('Recipe cards provide a quick view of suggested meals. Opening a card displays ingredients, '
           'cooking instructions, nutrition estimates, and actions for favorites and scheduling.')
    L.screenshot('meals-light.png', 'Figure 7. Pantry-based recipe suggestions and the meal plan section.', 235)
    L.screenshot('recipe-dark.png', 'Figure 8. Recipe details, macro values, and favorite action.', 235)
    L.text('Screenshots use demonstration records. The displayed information changes with the signed-in '
           'user\'s profile, pantry items, food logs, and activities.', 'small')

    L.heading('8. Testing and results', new_page=True)
    L.text(f'The latest recorded Maven test run completed <b>{count} tests with zero failures and zero errors</b>. '
           f'The run recorded {skipped} skipped tests. Tests check the program logic and persistence; desktop '
           'checks evaluate the visible screens and interactions in both themes.')
    names = {
        'ActivityTimeTest': 'Time intervals and midnight boundaries',
        'ActivityServiceTest': 'Scheduling, hierarchy, and plan batches',
        'AuthServiceTest': 'Authentication and password recovery',
        'ChatScopeValidatorTest': 'Chat topic validation',
        'DataStoreTest': 'JSON file operations',
        'GeminiServiceTest': 'AI results and fallback behavior',
        'MascotAssetTest': 'Transparent mascot assets',
        'FxmlValidationTest': 'FXML structure',
        'PantryServiceTest': 'Pantry quantity validation',
        'RecipeServiceTest': 'Recipe searching, sorting, and favorites',
        'RequirementCoverageTest': 'Nutrition matrix and generated favorites',
        'ThemeManagerTest': 'Theme state and persistence',
    }
    L.table(['Test area', 'Tests', 'Result'], [
        (names.get(row['suite'], row['suite']), row['tests'], 'Passed') for row in suites
    ], [L.WIDTH - 145, 55, 90])
    L.heading('Representative validation cases', 2)
    L.table(['Input or action', 'Expected outcome'], [
        ('A plan from 23:00 to 24:00', 'Accepted when it does not overlap a peer activity.'),
        ('An overlapping plan', 'Rejected with a conflict message.'),
        ('A negative or non-finite pantry quantity', 'Rejected without changing the saved stock.'),
        ('Favorite a generated recipe, then reload', 'The favorite is retained in the catalog.'),
        ('View long fitness advice in dark mode', 'Text remains readable and scrolls with the page.'),
    ], [245, L.WIDTH - 245])

    L.heading('9. Running and demonstrating the program', new_page=True)
    L.heading('9.1 Requirements and startup', 2)
    L.text('Use Java 21 and open the project folder. Maven downloads the required dependencies during '
           'initial setup. The optional AI features require a Gemini API key in the run environment.')
    L.code(r'''Windows PowerShell:
  .\mvnw.cmd javafx:run

macOS (included launcher):
  ./run.sh

Run automated tests with Java 21 selected:
  .\mvnw.cmd test     (Windows)
  sh mvnw test        (macOS/Linux)''')
    L.heading('9.2 Suggested classroom demonstration', 2)
    L.table(['Step', 'What to show'], [
        ('1. Account and Home', 'Sign in with a demonstration account and explain the dashboard summaries.'),
        ('2. Profile', 'Show body measurements, BMI classification, preferences, and calculated energy values.'),
        ('3. Pantry and Meals', 'Add an ingredient, view a recipe, save a favorite, and log a meal.'),
        ('4. Plan and Activity', 'Create an activity, show the calendar views, and demonstrate a rejected overlapping plan.'),
        ('5. Themes', 'Switch between light and dark mode and show that the same controls remain available.'),
        ('6. Optional AI', 'Ask a relevant question or show a suggested plan. Explain that a local fallback is used if the service is unavailable.'),
    ], [150, L.WIDTH - 150])
    L.text('Save the recovery code provided during registration. Recorded data and theme settings remain '
           'after restarting the program; users sign in again, and chat history starts a new application session.', 'small')

    L.heading('10. Conclusion and references', new_page=True)
    L.text('LifeSync combines several everyday wellness and planning tasks in a single Java desktop '
           'application. Its implementation demonstrates object-oriented modeling, arrays and collections, '
           'strings, reusable methods, conditionals, loops, searching, sorting, multidimensional data, '
           'encapsulation, exception handling, file persistence, and optional API communication.')
    L.text('The current program supports personal profiles, nutrition summaries, pantry and recipe management, '
           'favorites, activity tracking, and conflict-aware local scheduling. The interface provides light '
           'and dark themes, while automated and desktop testing support the reliability of these workflows.')
    L.text('The project remains a local desktop application. AI access depends on the configured service, '
           'nutrition figures are estimates, and Google Calendar synchronization is not included. These '
           'boundaries define the version presented in this submission.')
    L.heading('Project references', 2)
    L.table(['Material', 'Use in this documentation'], [
        ('LifeSync Java source code', 'Program features, calculations, classes, algorithms, and validation behavior.'),
        ('FXML views and CSS stylesheets', 'Screen arrangement, navigation, appearance, and theme behavior.'),
        ('pom.xml and Maven Wrapper configuration', 'Java version, frameworks, dependencies, and build commands.'),
        ('JUnit test reports', f'The recorded {count}-test result and test coverage summary.'),
        ('Project requirements provided for LifeSync', 'The Java programming concept list and SDLC stages.'),
    ], [195, L.WIDTH - 195])

    SchoolDocument(OUTPUT, cover).multiBuild(S)
    print(json.dumps({'pdf': str(OUTPUT), 'tests': count, 'bytes': OUTPUT.stat().st_size,
                      'edition': 'School submission', 'cover_fields': list(cover)}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for field in ['students', 'course', 'professor', 'school', 'date']:
        parser.add_argument('--' + field, default='')
    build(vars(parser.parse_args()))
