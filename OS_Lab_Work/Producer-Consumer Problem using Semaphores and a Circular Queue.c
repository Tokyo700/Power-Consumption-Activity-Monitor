#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <semaphore.h>
#include <unistd.h>

int *queue;
int capacity;
int total_items;
int in = 0;
int out = 0;

sem_t empty;
sem_t full;
sem_t mutex;

void *producer(void *arg)
{
    for (int i = 1; i <= total_items; i++)
    {
        sem_wait(&empty);
        sem_wait(&mutex);

        queue[in] = i;
        printf("Producer produced item %d at position %d\n", i, in);
        in = (in + 1) % capacity;

        sem_post(&mutex);
        sem_post(&full);
    }
    return NULL;
}

void *consumer(void *arg)
{
    for (int i = 1; i <= total_items; i++)
    {
        sem_wait(&full);
        sem_wait(&mutex);

        int item = queue[out];
        printf("Consumer consumed item %d from position %d\n", item, out);
        out = (out + 1) % capacity;

        sem_post(&mutex);
        sem_post(&empty);

        sleep(1);
    }
    return NULL;
}

int main()
{
    pthread_t prod, cons;

    printf("Enter the size of the circular queue: ");
    scanf("%d", &capacity);
    printf("Enter the number of items to produce: ");
    scanf("%d", &total_items);

    queue = (int *)malloc(capacity * sizeof(int));

    sem_init(&empty, 0, capacity);
    sem_init(&full, 0, 0);
    sem_init(&mutex, 0, 1);

    pthread_create(&prod, NULL, producer, NULL);
    pthread_create(&cons, NULL, consumer, NULL);

    pthread_join(prod, NULL);
    pthread_join(cons, NULL);

    sem_destroy(&empty);
    sem_destroy(&full);
    sem_destroy(&mutex);
    free(queue);

    return 0;
}