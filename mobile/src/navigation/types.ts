import type { NavigatorScreenParams } from '@react-navigation/native';

export type RootTabParamList = {
  Map: undefined;
  Board: undefined;
  SavedRoutes: undefined;
  Profile: undefined;
};

export type RootStackParamList = {
  Tabs: NavigatorScreenParams<RootTabParamList> | undefined;
  CourseDetail: { courseId: string };
  CourseEdit: { courseId: string };
};
