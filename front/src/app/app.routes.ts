import { Routes } from '@angular/router';
import { MappingsComponent } from './mappings/mappings.component';
import { SearchComponent } from './search/search.component';

export const routes: Routes = [
  { path: 'search', component: SearchComponent, title: 'Consulta' },
  { path: 'mappings', component: MappingsComponent, title: 'Mappings' },
  { path: '**', redirectTo: 'search' },
];
