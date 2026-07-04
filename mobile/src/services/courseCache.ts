import { getCourse } from './courseApi';
import { Course } from '../types';

const cache = new Map<string, Course>();
const pending = new Map<string, Promise<Course>>();

export function getCachedCourse(courseId: string, accessToken?: string): Promise<Course> {
  const cached = cache.get(courseId);
  if (cached) return Promise.resolve(cached);

  const inflight = pending.get(courseId);
  if (inflight) return inflight;

  const promise = getCourse(courseId, accessToken)
    .then(course => {
      cache.set(courseId, course);
      pending.delete(courseId);
      return course;
    })
    .catch(err => {
      pending.delete(courseId);
      throw err;
    });

  pending.set(courseId, promise);
  return promise;
}

export function evictCourse(courseId: string): void {
  cache.delete(courseId);
  pending.delete(courseId);
}
