import { Routes } from '@angular/router';
import { requireProfile, requireSession } from './core/guards';
import { HomePage } from './pages/home.page';
import { CoursesPage, CatalogPage } from './pages/catalog.page';
import { PracticePage } from './pages/practice.page';
import { ProgressPage, BadgesPage } from './pages/progress.page';
import { AuthPage, CompleteProfilePage } from './pages/auth.page';
import { SettingsPage } from './pages/settings.page';
import { StudentRoomsPage } from './pages/student-room.page';
import { TeacherQuestionsPage, QuestionDetailPage } from './pages/teacher-questions.page';
import { TeacherQuizzesPage } from './pages/teacher-quizzes.page';
import { TeacherRoomsPage, RoomDetailPage } from './pages/teacher-rooms.page';
import { NotFoundPage } from './pages/not-found.page';

// Las rutas usan #/... igual que la versión anterior, para conservar enlaces y filtros.
export const routes: Routes = [
  { path: '', component: HomePage, canActivate: [requireProfile] },
  { path: 'cursos', component: CoursesPage, canActivate: [requireProfile] },
  { path: 'retos', component: CatalogPage, canActivate: [requireProfile] },
  { path: 'reto/:id', component: PracticePage, canActivate: [requireProfile] },
  { path: 'progreso', component: ProgressPage, canActivate: [requireProfile] },
  { path: 'insignias', component: BadgesPage, canActivate: [requireProfile] },
  { path: 'salas', component: StudentRoomsPage, canActivate: [requireProfile] },
  { path: 'docente/preguntas', component: TeacherQuestionsPage, canActivate: [requireProfile, requireSession] },
  { path: 'docente/preguntas/:id', component: QuestionDetailPage, canActivate: [requireProfile, requireSession] },
  { path: 'docente/quizzes', component: TeacherQuizzesPage, canActivate: [requireProfile, requireSession] },
  { path: 'docente/salas', component: TeacherRoomsPage, canActivate: [requireProfile, requireSession] },
  { path: 'docente/salas/:id', component: RoomDetailPage, canActivate: [requireProfile, requireSession] },
  { path: 'acceso', component: AuthPage },
  { path: 'completar-perfil', component: CompleteProfilePage },
  { path: 'ajustes', component: SettingsPage },
  { path: '**', component: NotFoundPage },
];
